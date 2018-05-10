package com.galacticfog.gestalt

import java.util.UUID

import akka.pattern.ask
import akka.actor.ActorRef
import com.galacticfog.gestalt.caas.dcos.DCOSAuthTokenActor
import javax.inject.{Inject, Named, Singleton}
import modules.WSClientFactory
import play.api.http.HeaderNames
import play.api.libs.json._
import play.api.{Configuration, Logger}
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Try

trait CaasClient {
  def getCurrentImage(serviceName: String): Future[String]
}

trait CaasClientFactory {
  def getClient: Future[CaasClient]
}

@Singleton
class DefaultCaasClientFactory @Inject() ( wsFactory: WSClientFactory,
                                           config: Configuration,
                                           meta: MetaClient,
                                           @Named(DCOSAuthTokenActor.actorName) authTokenActor: ActorRef)
                                         ( implicit ec: ExecutionContext ) extends CaasClientFactory {

  val caasProviderId = UUID.fromString(config.get[String]("caas.id"))

  override def getClient: Future[CaasClient] = {
    for {
      providers <- meta.listProviders
      provider = providers.find(_.id == caasProviderId) getOrElse {throw new RuntimeException(s"could not location CaaS provider with id '${caasProviderId}'")}
      client = provider.providerType match {
        case ResourceIds.DcosProvider => new DefaultDcosCaasClient(wsFactory, config, provider, authTokenActor)
        case unsupported => throw new RuntimeException(s"Provider has resource type ${resourceName(unsupported)}")
      }
    } yield client
  }
}

class DefaultDcosCaasClient( wsFactory: WSClientFactory,
                             config: Configuration,
                             provider: MetaProvider,
                             authTokenActor: ActorRef )
                           ( implicit executionContext: ExecutionContext ) extends CaasClient {

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

  override def getCurrentImage(serviceName: String): Future[String] = {
    log.info(s"looking up ${serviceName} against CaaS API")
    for {
      req <- genRequest(s"/v2/apps/${appGroupPrefix}/${serviceName}")
      resp <- req.get()
      json <- processResponse(resp)
      image <- (json \ "app" \ "container" \ "docker" \ "image").validate[String] match {
        case JsSuccess(img,_) =>
          log.info(s"${serviceName} has image: $img")
          Future.successful(img)
        case JsError(_) =>
          Future.failed(new RuntimeException("could not determine docker image from Marathon app response"))
      }
    } yield image
  }

}
