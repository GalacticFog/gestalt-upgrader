package com.galacticfog.gestalt.caas.dcos

import akka.actor.ActorRef
import akka.pattern.ask
import com.galacticfog.gestalt.{BaseService, BaseServiceProto, MetaProvider}
import com.galacticfog.gestalt.caas.CaasClient
import modules.WSClientFactory
import play.api.http.HeaderNames
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.{Configuration, Logger}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object DefaultDcosCaasClient {
  case class DcosBaseService(name: String, json: JsObject) extends BaseService {
    override def image: String = (json \ "app" \ "container" \ "docker" \ "image").asOpt[String].getOrElse(
      throw new RuntimeException("could not parse '.container.docker.image' from Marathon app response")
    )
    override def numInstances: Int = (json \ "app" \ "instances").asOpt[Int].getOrElse(
      throw new RuntimeException("could not parse '.instances' from Marathon app response")
    )
  }
}

class DefaultDcosCaasClient( wsFactory: WSClientFactory,
                             config: Configuration,
                             provider: MetaProvider,
                             authTokenActor: ActorRef )
                           ( implicit executionContext: ExecutionContext ) extends CaasClient {

  import DefaultDcosCaasClient._

  val log = Logger(this.getClass)

  val ACS_TOKEN_REQUEST_TIMEOUT = "acs_token_request_timeout"
  val AUTH_CONFIG = "auth"
  val DEFAULT_ACS_TOKEN_REQUEST_TIMEOUT: FiniteDuration = 10 seconds

  val acceptAnyCertificate: Boolean = (provider.config \ "accept_any_cert").asOpt[Boolean].getOrElse(false)
  val appGroupPrefix: String = (provider.config \ "appGroupPrefix").asOpt[String]
    .getOrElse {throw new RuntimeException("DC/OS provider must have property 'appGroupPrefix'")}

  val dcosUrl = (provider.config \ "dcos_url").asOpt[String]
  val marathonBaseUrl = (provider.config \ "url").asOpt[String]
    .orElse(dcosUrl.map(_.stripSuffix("/") + "/service/marathon"))
    .getOrElse {throw new RuntimeException("DC/OS provider must have one of 'properties.config.dcos_url' or 'properties.config.url'")}

  val client: WSClient = wsFactory.getClient(acceptAnyCertificate)

  val acsTokenRequestTimeout = (provider.config \ ACS_TOKEN_REQUEST_TIMEOUT)
    .asOpt[String]
    .flatMap(s => Try(Duration(s)).toOption)
    .collect({case fd: FiniteDuration => fd})
    .getOrElse(DEFAULT_ACS_TOKEN_REQUEST_TIMEOUT)

  val acsTokenRequest = {
    val authJson = (provider.config \ AUTH_CONFIG).asOpt[JsObject] getOrElse Json.obj()
    (authJson \ "scheme").asOpt[String] flatMap {
      case "acs" =>
        val tokReq = for {
          id <- (authJson \ "service_account_id").asOpt[String]
          key <- (authJson \ "private_key").asOpt[String]
          url <- (authJson \ "dcos_base_url").asOpt[String]
        } yield DCOSAuthTokenActor.DCOSAuthTokenRequest(provider.id, acceptAnyCertificate, id, key, url)
        Some(tokReq.getOrElse {
          throw new RuntimeException("provider with 'acs' authentication was missing required fields")
        })
      case _ =>
        log.debug("provisioning MarathonClient without authentication credentials")
        None
    }
  }

  private[this] def processResponse(resp: WSResponse): Future[JsValue] = resp match {
    case r if r.status == 204 => Future.successful(Json.obj())
    case r if Range(200, 299).contains(r.status) => Future.successful(r.json)
    case r => Future.failed(new RuntimeException(s"Meta API returned ${r.statusText}: ${r.body}"))
  }

  private[this] def getAuthToken: Future[Option[String]] = {
    acsTokenRequest match {
      case None =>
        Future.successful(None)
      case Some(auth) =>
        val f = authTokenActor.ask(auth)(5 seconds)
        f.flatMap({
          case DCOSAuthTokenActor.DCOSAuthTokenResponse(tok) => Future.successful(Some(tok))
          case DCOSAuthTokenActor.DCOSAuthTokenError(msg)    => Future.failed(new RuntimeException(s"error retrieving ACS token: ${msg}"))
          case other                                         => Future.failed(new RuntimeException(s"unexpected return from DCOSAuthTokenActor: $other"))
        })
    }
  }

  private[this] def genRequest(endpoint: String): Future[WSRequest] = {
    val url = s"${marathonBaseUrl}/${endpoint.stripPrefix("/")}"
    for {
      acsToken <- getAuthToken
      req = acsToken.foldLeft(client.url(url)) {
        case (req, token) => req.withHttpHeaders(HeaderNames.AUTHORIZATION -> s"token=${token}")
      }
    } yield req
  }

  override def getService(serviceName: String): Future[BaseService] = {
    log.info(s"looking up '$serviceName' against CaaS API")
    for {
      req <- genRequest(s"/v2/apps/$appGroupPrefix/$serviceName")
      resp <- req.get()
      json <- processResponse(resp)
    } yield DcosBaseService(serviceName, (json \ "app").as[JsObject])
  }

  override def update(service: BaseService, target: BaseServiceProto): Future[BaseService] = {
    val imageUpdater = __.json.pickBranch(
      (__ \ "container" \ "docker" \ "image").json.update( __.read(Reads.pure(JsString(target.image))) )
        andThen
        (__ \ "version").json.prune
    )
    updateMarathonApp(service, imageUpdater)
  }

  private[this] def updateMarathonApp(service: BaseService, update: Reads[JsObject]): Future[DcosBaseService] = {
    for {
      dcosApp <- Future.fromTry(Try(service.asInstanceOf[DcosBaseService]))
      _ = log.debug(s"old app: ${dcosApp.json}")
      newJson <- Future.fromTry(Try(dcosApp.json.transform(update).get))
      _ = log.debug(s"new app: $newJson")
      putReq <- genRequest(s"/v2/apps/$appGroupPrefix/${service.name}")
      putResp <- putReq.put(newJson)
      _ <- if (putResp.status == 200) Future.successful(()) else Future.failed(
        new RuntimeException(putResp.body)
      )
    } yield dcosApp.copy(
      json = newJson
    )
  }

  override def scale(service: BaseService, numInstance: Int): Future[BaseService] = {
    val scaleUpdater = __.json.pickBranch(
      (__ \ "instances" \ "docker" \ "image").json.update( __.read(Reads.pure(JsNumber(numInstance))) )
        andThen
        (__ \ "version").json.prune
    )
    updateMarathonApp(service, scaleUpdater)
  }
}
