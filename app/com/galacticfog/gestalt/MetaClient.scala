package com.galacticfog.gestalt

import java.util.{Base64, UUID}

import javax.inject.Inject
import play.api.{Configuration, Logger}
import play.api.http.{HeaderNames, Status}
import play.api.libs.json.{JsObject, JsValue, Json, Reads}
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class ResourceNotFoundException(msg: String) extends RuntimeException(msg)

trait MetaClient {
  def getProvider(fqon: String, id: UUID): Future[MetaProvider]
  def listProviders: Future[Seq[MetaProvider]]
  def updateProvider(metaProvider: MetaProvider, newProto: MetaProviderProto): Future[MetaProvider]
  def performMigration(version: String): Future[String]
}

trait MetaClientParsing {
  val logger = Logger(this.getClass)

  def parseMetaProvider(json: JsObject): Option[MetaProvider] = {
    val rdProviderId = new Reads[UUID] {
      override def reads(json: JsValue): JsResult[UUID] = {
        json.validate[String].flatMap {
          tpeLbl => Try{resourceId(tpeLbl)}.fold(
            _ => JsError(s"could not determine provider resource_type for label '${tpeLbl}'"),
            JsSuccess(_)
          )
        }
      }
    }

    val rdsServiceProviderImage = new Reads[String] {
      override def reads(json: JsValue): JsResult[String] = {
        (json \ "properties" \ "services").asOpt[Seq[JsObject]].getOrElse(Seq.empty).headOption match {
          case None => JsError("no services")
          case Some(svc) => (svc \ "container_spec" \ "properties" \ "image").validate[String]
        }
      }
    }

    val rdsExecutorProviderImage: Reads[String] = (
      (__ \ "properties" \ "config" \ "env" \ "public" \ "IMAGE").read[String]
    )

    val metaProviderRds: Reads[MetaProvider] = (
      (__ \ "org" \ "properties" \ "fqon").read[String] and
        (__ \ "name").read[String] and
        (__ \ "id").read[UUID] and
        (__ \ "resource_type").read[UUID](rdProviderId) and
        __.read[String](rdsServiceProviderImage orElse rdsExecutorProviderImage).map(Option(_)).orElse(Reads.pure(None)) and
        (__ \ "properties" \ "config").read[JsObject].orElse(Reads.pure(Json.obj())) and
        (__ \ "properties" \ "services").read[Seq[JsObject]].orElse(Reads.pure(Seq.empty))
      )(MetaProvider.apply(_,_,_,_,_,_,_))
    json.validate[MetaProvider](metaProviderRds).asOpt
  }

  def messageOrBody(resp: WSResponse) = Try((resp.json \ "message").as[String]).getOrElse(resp.body)

  def processResponse(resp: WSResponse): Future[JsValue] = resp match {
    case r if r.status == Status.NO_CONTENT => Future.successful(Json.obj())
    case r if r.status == Status.ACCEPTED  => Future.successful(Try(r.json).getOrElse(Json.obj()))
    case r if Range(200, 299).contains(r.status) => Future.successful(r.json)
    case r if r.status == Status.NOT_FOUND => Future.failed(new ResourceNotFoundException("resource not found"))
    case r =>
      logger.error(s"Meta API returned ${r.statusText}: ${messageOrBody(r)}")
      Future.failed(new RuntimeException(s"Meta API returned ${r.statusText}: ${messageOrBody(r)}"))
  }
}

class DefaultMetaClient @Inject() ( ws: WSClient, config: Configuration )
                                  ( implicit ec: ExecutionContext )
  extends MetaClient with MetaClientParsing {

  val metaBaseUrl = config.get[String]("meta.callback-url").stripSuffix("/")

  val secKey = config.get[String]("security.key")
  val secSecret = config.get[String]("security.secret")
  val authHeader = HeaderNames.AUTHORIZATION -> ("Basic " + new String(Base64.getEncoder.encode(s"$secKey:$secSecret".getBytes)))

  private[this] def genRequest(endpoint: String, qs: (String,String)*): WSRequest = {
    val ep = endpoint.stripPrefix("/")
    ws.url(s"$metaBaseUrl/$ep")
      .withHttpHeaders(authHeader)
      .withQueryStringParameters(qs:_*)
  }

  private[this] def get(endpoint: String, qs: (String,String)*): Future[JsValue] = {
    genRequest(endpoint, qs:_*).execute("GET") flatMap processResponse recoverWith {
      case e: Throwable =>
        logger.error(s"error during GET(${endpoint})", e)
        Future.failed(e)
    }
  }

  private[this] def post(endpoint: String, qs: (String,String)*): Future[JsValue] = {
    genRequest(endpoint, qs:_*).execute("POST") flatMap processResponse recoverWith {
      case e: Throwable =>
        logger.error(s"error during POST(${endpoint})", e)
        Future.failed(e)
    }
  }

  private[this] def patch(endpoint: String, payload: JsValue, qs: (String,String)*): Future[JsValue] = {
    logger.info(s"PATCH : $payload")
    genRequest(endpoint, qs:_*).patch(payload) flatMap processResponse recoverWith {
      case e: Throwable =>
        logger.error(s"error during PATCH(${endpoint}", e)
        Future.failed(e)
    }
  }

  override def listProviders: Future[Seq[MetaProvider]] = {
    val l = for {
      orgs <- get("orgs", "expand" -> "true")
      fqons = orgs.as[Seq[JsObject]].flatMap {o => (o \ "properties" \ "fqon").asOpt[String]}
      providerLists <- Future.traverse(fqons) {
        fqon => get(s"$fqon/providers", "expand" -> "true") map {_.as[Seq[JsObject]]}
      }
      allProviders = providerLists.flatMap { _.flatMap(parseMetaProvider(_)) }
    } yield allProviders.distinct
    l.onComplete({
      case Success(l) => logger.info(s"listProviders returned ${l.size} providers")
      case Failure(e) => logger.error("listProviders returned exception", e)
    })
    l
  }

  override def updateProvider(p: MetaProvider, newProto: MetaProviderProto): Future[MetaProvider] = {
    def processPatch(j: JsValue) = parseMetaProvider(j.as[JsObject]) match {
      case Some(mp) => Future.successful(mp)
      case None => Future.failed(new RuntimeException("failed to parse MetaProvider from expected PATCH payload"))
    }
    def patchExecutor() = patch(s"/${p.fqon}/providers/${p.id}", Json.arr(Json.obj(
      "op" -> "replace",
      "path" -> "/properties/config/env/public/IMAGE",
      "value" -> newProto.image
    )), "expand" -> "true")
    def patchService() = {
      val imgUpdater = (__ \ "container_spec" \ "properties" \ "image").json.update(Reads.pure(JsString(newProto.image)))
      p.services.headOption match {
        case None => Future.failed(new RuntimeException("provider did not have any services"))
        case Some(svc) => svc.transform(imgUpdater) match {
          case JsError(_) => Future.failed(new RuntimeException("failed to patch service"))
          case JsSuccess(updatedSvc,_) =>
            patch(s"/${p.fqon}/providers/${p.id}", Json.arr(Json.obj(
              "op" -> "replace",
              "path" -> "/properties/services",
              "value" -> Json.arr(updatedSvc)
            )))
        }
      }
    }

    if (executorProviders.contains(p.providerType)) {
      patchExecutor() flatMap processPatch
    } else for {
      updated <- patchService flatMap processPatch
      _ <- post(s"/${p.fqon}/providers/${p.id}/redeploy")
    } yield updated
  }

  override def getProvider(fqon: String, id: UUID): Future[MetaProvider] = {
    get(s"$fqon/providers/$id") flatMap { j =>
      parseMetaProvider(j.as[JsObject]) match {
        case Some(p) => Future.successful(p)
        case None => Future.failed(new RuntimeException("failed to parse MetaProvider from expected GET payload"))
      }
    } recoverWith {
      case rnf: ResourceNotFoundException => Future.failed(new ResourceNotFoundException(s"provider ${id} not found"))
    }
  }

  override def performMigration(version: String): Future[String] = {
    post(s"/migrate", "version" -> version).map {
      j => (j \ "message").asOpt[String].getOrElse("Migration complete")
    }
  }
}
