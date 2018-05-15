package com.galacticfog.gestalt.caas

import java.util.UUID

import akka.actor.{ActorLogging, ActorRef}
import akka.pattern.pipe
import akka.persistence.{PersistentActor, RecoveryCompleted}
import com.galacticfog.gestalt._
import com.galacticfog.gestalt.caas.dcos.{DCOSAuthTokenActor, DefaultDcosCaasClient}
import javax.inject.{Inject, Named}
import modules.WSClientFactory
import play.api.Configuration

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait CaasClient {
  def getCurrentImage(serviceName: String): Future[String]
  def updateImage(serviceName: String, newImage: String, expectedImages: Seq[String]): Future[String]
}

object CaasClientFactory {
  final val actorName = "caas-client-factory"

  case object GetClient

  case class DiscoveredCaasProvider(provider: MetaProvider)
}

class DefaultCaasClientFactory @Inject() ( wsFactory: WSClientFactory,
                                           config: Configuration,
                                           meta: MetaClient,
                                           @Named(DCOSAuthTokenActor.actorName) authTokenActor: ActorRef) extends PersistentActor with ActorLogging {

  import CaasClientFactory._

  override def persistenceId: String = "default-caas-client-factory"

  val caasProviderId = UUID.fromString(config.get[String]("caas.id"))

  private[this] var fCaasProvider: Future[MetaProvider] = null
  private[this] var providerRecovered = false

  implicit val ec = context.dispatcher

  override def receiveRecover: Receive = {
    case RecoveryCompleted =>
      if (!providerRecovered) {
        log.info(s"recovery complete without discovering journaled CaaS provider; will query meta for provider ${caasProviderId}")
        // update our state
        fCaasProvider = for {
          providers <- meta.listProviders
          provider = providers.find(_.id == caasProviderId) getOrElse {throw new RuntimeException(s"could not location CaaS provider with id '${caasProviderId}'")}
          _ = log.info(s"discovered CaaS provider ${provider.name} (${provider.id})")
        } yield provider
        // also, message self so that we can persist this
        fCaasProvider onComplete {
          _ match {
            case Success(provider) =>
              self ! DiscoveredCaasProvider(provider)
            case Failure(t) =>
              log.error(t, s"Error query meta for provider ${caasProviderId}")
          }
        }
      }
    case DiscoveredCaasProvider(provider) =>
      log.info(s"recovered journaled CaaS provider: ${provider.name} (${provider.id})")
      fCaasProvider = Future.successful(provider)
      providerRecovered = true
  }

  override def receiveCommand: Receive = {
    case d: DiscoveredCaasProvider =>
      persist(d) {_ => log.info(s"persisted CaaS provider ${d.provider.name} (${d.provider.id})")}
    case GetClient =>
      fCaasProvider.flatMap(provider =>
        provider.providerType match {
          case ResourceIds.DcosProvider =>
            Future.successful(new DefaultDcosCaasClient(wsFactory, config, provider, authTokenActor))
          case unsupported =>
            Future.failed(new RuntimeException(s"Provider has resource type ${resourceName(unsupported)}"))
        }
      ).pipeTo(sender())
  }

}
