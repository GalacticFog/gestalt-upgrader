package com.galacticfog.gestalt

import java.util.UUID

import akka.actor.{Actor, ActorLogging}
import akka.pattern.pipe
import javax.inject.Inject

object Planner {
  final val actorName = "planner"

  case object ComputePlan
  case class UpgradePlan(steps: Seq[UpgradeStep])
}

class Planner16 @Inject() ( caasClientFactory: CaasClientFactory,
                            metaClient: MetaClient ) extends Actor with ActorLogging {

  import Planner._

  val expectedVersion = "1.5.0"
  val targetVersion   = "1.6.0"

  val caasClient = caasClientFactory.getClient

  implicit val ec = context.dispatcher

  val providerUpgrades: Map[UUID, (MetaProviderProto, MetaProviderProto)] = Map(
    ResourceIds.KongGateway    ->    (MetaProviderProto(s"galacticfog/kong:release-${expectedVersion}"),                          MetaProviderProto(s"galacticfog/kong:release-${targetVersion}")),
    ResourceIds.LambdaProvider ->    (MetaProviderProto(s"galacticfog/gestalt-laser:release-${expectedVersion}"),                 MetaProviderProto(s"galacticfog/gestalt-laser:release-${targetVersion}")),
    ResourceIds.GatewayManager ->    (MetaProviderProto(s"galacticfog/gestalt-api-gateway:release-${expectedVersion}"),           MetaProviderProto(s"galacticfog/gestalt-api-gateway:release-${targetVersion}")),
    ResourceIds.GoLangExecutor    -> (MetaProviderProto(s"galacticfog/gestalt-laser-executor-go:release-${expectedVersion}"),     MetaProviderProto(s"galacticfog/gestalt-laser-executor-go:release-${targetVersion}")),
    ResourceIds.JavaExecutor      -> (MetaProviderProto(s"galacticfog/gestalt-laser-executor-jvm:release-${expectedVersion}"),    MetaProviderProto(s"galacticfog/gestalt-laser-executor-jvm:release-${targetVersion}")),
    ResourceIds.NashornExecutor   -> (MetaProviderProto(s"galacticfog/gestalt-laser-executor-js:release-${expectedVersion}"),     MetaProviderProto(s"galacticfog/gestalt-laser-executor-js:release-${targetVersion}")),
    ResourceIds.NodeJsExecutor    -> (MetaProviderProto(s"galacticfog/gestalt-laser-executor-nodejs:release-${expectedVersion}"), MetaProviderProto(s"galacticfog/gestalt-laser-executor-nodejs:release-${targetVersion}")),
    ResourceIds.PythonExecutor    -> (MetaProviderProto(s"galacticfog/gestalt-laser-executor-python:release-${expectedVersion}"), MetaProviderProto(s"galacticfog/gestalt-laser-executor-python:release-${targetVersion}")),
    ResourceIds.RubyExecutor      -> (MetaProviderProto(s"galacticfog/gestalt-laser-executor-ruby:release-${expectedVersion}"),   MetaProviderProto(s"galacticfog/gestalt-laser-executor-ruby:release-${targetVersion}")),
    ResourceIds.CsharpExecutor    -> (MetaProviderProto(s"galacticfog/gestalt-laser-executor-dotnet:release-${expectedVersion}"), MetaProviderProto(s"galacticfog/gestalt-laser-executor-dotnet:release-${targetVersion}"))
  )

  val executors: Set[UUID] = Set(
    ResourceIds.GoLangExecutor,
    ResourceIds.JavaExecutor,
    ResourceIds.NashornExecutor,
    ResourceIds.NodeJsExecutor,
    ResourceIds.PythonExecutor,
    ResourceIds.RubyExecutor,
    ResourceIds.CsharpExecutor
  )

  override def receive: Receive = {
    case ComputePlan =>
      log.info("received ComputePlan, beginning plan computation...")
      val fMeta = caasClient.flatMap(_.getCurrentImage("meta"))
      val fSec  = caasClient.flatMap(_.getCurrentImage("security"))
      val fUI   = caasClient.flatMap(_.getCurrentImage("ui-react"))

      val plan = for {
        // BASE
        sec  <- fSec
        meta <- fMeta
        ui   <- fUI
        // Providers
        providers <- metaClient.listProviders
        (execs, provs) = providers.partition(p => executors.contains(p.providerType))
        updateExecs = execs.flatMap {
          p => providerUpgrades.get(p.providerType) map {
            case (exp,tgt) => UpgradeExecutor(exp, tgt, p)
          }
        }
        updatedProviders = provs.flatMap {
          p => providerUpgrades.get(p.providerType) map {
            case (exp,tgt) => UpgradeProvider(exp, tgt, p)
          }
        }
      } yield UpgradePlan(Seq(
        BackupDatabase,
        UpgradeBaseService("security", s"galacticfog/gestalt-security:release-${expectedVersion}", s"galacticfog/gestalt-security:release-${targetVersion}", sec),
        UpgradeBaseService(    "meta",     s"galacticfog/gestalt-meta:release-${expectedVersion}",     s"galacticfog/gestalt-meta:release-${targetVersion}", meta),
        UpgradeBaseService(      "ui", s"galacticfog/gestalt-ui-react:release-${expectedVersion}", s"galacticfog/gestalt-ui-react:release-${targetVersion}", ui)
      ) ++ updateExecs ++ updatedProviders)

      plan pipeTo sender()
  }
}
