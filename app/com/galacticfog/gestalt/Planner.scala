package com.galacticfog.gestalt

import java.util.UUID

import akka.actor.Actor
import akka.pattern.pipe
import javax.inject.Inject

object Planner {
  final val actorName = "planner"

  case object ComputePlan
  case class UpgradePlan(steps: Seq[UpgradeStep])
}

class Planner16 @Inject() ( caasClientFactory: CaasClientFactory,
                            metaClient: MetaClient ) extends Actor {

  import Planner._

  val expectedVersion = "1.5.0"
  val targetVersion   = "1.6.0"

  val caasClient = caasClientFactory.getClient

  implicit val ec = context.dispatcher

  val providerUpgrades: Map[UUID, (MetaProviderProto, MetaProviderProto)] = Map(
    ResourceIds.KongGateway    -> (MetaProviderProto(s"galacticfog/kong:release-${expectedVersion}"), MetaProviderProto(s"galacticfog/kong:release-${targetVersion}")),
    ResourceIds.LambdaProvider -> (MetaProviderProto(s"galacticfog/gestalt-laser:release-${expectedVersion}"), MetaProviderProto(s"galacticfog/gestalt-laser:release-${targetVersion}")),
    ResourceIds.GatewayManager -> (MetaProviderProto(s"galacticfog/gestalt-api-gateway:release-${expectedVersion}"), MetaProviderProto(s"galacticfog/gestalt-api-gateway:release-${targetVersion}"))
  )

  override def receive: Receive = {
    case ComputePlan =>
      val fMeta = caasClient.getCurrentImage("meta")
      val fSec  = caasClient.getCurrentImage("security")
      val fUI   = caasClient.getCurrentImage("ui")

      val plan = for {
        // BASE
        sec  <- fSec
        meta <- fMeta
        ui   <- fUI
        // Providers
        providers <- metaClient.listProviders
        updatedProviders = providers.flatMap {
          p => providerUpgrades.get(p.providerType) map {
            case (exp,tgt) => UpgradeProvider(exp, tgt, p, p.hasContainers)
          }
        }
      } yield UpgradePlan(Seq(
        UpgradeBaseService("security", s"galacticfog/gestalt-security:release-${expectedVersion}", s"galacticfog/gestalt-security:release-${targetVersion}", sec),
        UpgradeBaseService(    "meta",     s"galacticfog/gestalt-meta:release-${expectedVersion}",     s"galacticfog/gestalt-meta:release-${targetVersion}", meta),
        UpgradeBaseService(      "ui", s"galacticfog/gestalt-ui-react:release-${expectedVersion}", s"galacticfog/gestalt-ui-react:release-${targetVersion}", ui)
      ) ++ updatedProviders)

      plan pipeTo sender()
  }
}
