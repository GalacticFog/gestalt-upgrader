package com.galacticfog.gestalt

import java.util.{Base64, UUID}

import javax.inject.Inject
import play.api.Configuration
import play.api.http.HeaderNames
import play.api.libs.json.{JsObject, JsValue, Json, Reads}
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

import scala.concurrent.{ExecutionContext, Future}

trait MetaClient {
  def listProviders: Future[Seq[MetaProvider]]
}

trait MetaClientParsing {
  def parseMetaProvider(json: JsObject): Option[MetaProvider] = {
    val metaProviderRds: Reads[MetaProvider] = (
      (__ \ "org" \ "properties" \ "fqon").read[String] and
        (__ \ "name").read[String] and
        (__ \ "id").read[UUID] and
        (__ \ "resource_type").read[String].map(resourceId(_)) and
        (__ \ "properties" \ "services").readNullable[Seq[JsObject]].map(_.flatMap(_.headOption)).map(j => (j.getOrElse(Json.obj()) \ "container_spec" \ "properties" \ "image").asOpt[String]) and
        (__ \ "properties" \ "config").read[JsObject].orElse(Reads.pure(Json.obj()))
      )(MetaProvider.apply(_,_,_,_,_,_))
    json.asOpt[MetaProvider](metaProviderRds)
  }

  def processResponse(resp: WSResponse): Future[JsValue] = resp match {
    case r if r.status == 204 => Future.successful(Json.obj())
    case r if Range(200, 299).contains(r.status) => Future.successful(r.json)
    case r => Future.failed(new RuntimeException(s"Meta API returned ${r.statusText}: ${r.body}"))
  }
}

class DefaultMetaClient @Inject() (ws: WSClient, config: Configuration)(implicit ec: ExecutionContext) extends MetaClient with MetaClientParsing {

  val metaHost = config.get[String]("meta.host")
  val metaPort = config.get[Int]("meta.port")
  val secKey = config.get[String]("security.key")
  val secSecret = config.get[String]("security.secret")
  val authHeader = HeaderNames.AUTHORIZATION -> new String(Base64.getEncoder.encode(s"$secKey:$secSecret".getBytes))

  private[this] def genRequest(endpoint: String, qs: (String,String)*): WSRequest = {
    val ep = endpoint.stripPrefix("/")
    ws.url(s"http://$metaHost:$metaPort/$ep")
      .withHttpHeaders(authHeader)
      .withQueryStringParameters(qs:_*)
  }

  private[this] def get(endpoint: String, qs: (String,String)*): Future[JsValue] = {
    genRequest(endpoint, qs:_*).get() flatMap processResponse
  }

  override def listProviders: Future[Seq[MetaProvider]] = {
    for {
      orgs <- get("orgs", "expand" -> "true")
      fqons = orgs.as[Seq[JsObject]].flatMap {o => (o \ "properties" \ "fqon").asOpt[String]}
      providerLists <- Future.traverse(fqons) {
        fqon => get(s"$fqon/providers", "expand" -> "true") map {_.as[Seq[JsObject]]}
      }
      allProviders = providerLists.flatMap { _.flatMap(parseMetaProvider(_)) }
    } yield allProviders
  }
}
