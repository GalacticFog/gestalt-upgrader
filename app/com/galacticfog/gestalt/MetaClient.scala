package com.galacticfog.gestalt

import java.util.{Base64, UUID}

import javax.inject.Inject
import play.api.{Configuration, Logger}
import play.api.http.HeaderNames
import play.api.libs.json.{JsObject, JsValue, Json, Reads}
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait MetaClient {
  def listProviders: Future[Seq[MetaProvider]]
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

    val metaProviderRds: Reads[MetaProvider] = (
      (__ \ "org" \ "properties" \ "fqon").read[String] and
        (__ \ "name").read[String] and
        (__ \ "id").read[UUID] and
        (__ \ "resource_type").read[UUID](rdProviderId) and
        (__ \ "properties" \ "services").readNullable[Seq[JsObject]].map(_.flatMap(_.headOption)).map(j => (j.getOrElse(Json.obj()) \ "container_spec" \ "properties" \ "image").asOpt[String]) and
        (__ \ "properties" \ "config").read[JsObject].orElse(Reads.pure(Json.obj()))
      )(MetaProvider.apply(_,_,_,_,_,_))
    json.validate[MetaProvider](metaProviderRds).asOpt
  }

  def messageOrBody(resp: WSResponse) = Try((resp.json \ "message").as[String]).getOrElse(resp.body)

  def processResponse(resp: WSResponse): Future[JsValue] = resp match {
    case r if r.status == 204 => Future.successful(Json.obj())
    case r if Range(200, 299).contains(r.status) => Future.successful(r.json)
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
    genRequest(endpoint, qs:_*).get() flatMap processResponse recoverWith {
      case e: Throwable =>
        logger.error(s"error during GET(${endpoint})", e)
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
}
