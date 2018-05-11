package com.galacticfog.gestalt

import java.util.UUID

import akka.actor.{Actor, ActorLogging}
import akka.pattern.pipe
import javax.inject.Inject

import scala.concurrent.Future
import scala.language.implicitConversions

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

  def simpleProviderUpgrade(providerAndBaseImage: (UUID, String)): (UUID, (MetaProviderProto, MetaProviderProto)) =
    providerAndBaseImage._1 -> (
      MetaProviderProto(providerAndBaseImage._2 + expectedVersion) , MetaProviderProto(providerAndBaseImage._2 + targetVersion)
    )

  def simpleSvcUpgrade(svcAndBaseImage: (String, String)): (String, (String, String)) =
    svcAndBaseImage._1 -> (
      svcAndBaseImage._2 + expectedVersion, svcAndBaseImage._2 + targetVersion
    )

  val baseUpgrades = Map(
    "security" -> "galacticfog/gestalt-security:release-",
    "meta"     -> "galacticfog/gestalt-meta:release-",
    "ui-react" -> "galacticfog/gestalt-ui-react:release-"
  ) map simpleSvcUpgrade

  val providerUpgrades = Map(
    ResourceIds.KongGateway       -> "galacticfog/kong:release-",
    ResourceIds.LambdaProvider    -> "galacticfog/gestalt-laser:release-",
    ResourceIds.GatewayManager    -> "galacticfog/gestalt-api-gateway:release-",
    ResourceIds.PolicyProvider    -> "galacticfog/gestalt-policy:release-",
    ResourceIds.LoggingProvider   -> "galacticfog/gestalt-logger:release-",

    ResourceIds.GoLangExecutor    -> "galacticfog/gestalt-laser-executor-go:release-",
    ResourceIds.JavaExecutor      -> "galacticfog/gestalt-laser-executor-jvm:release-",
    ResourceIds.NashornExecutor   -> "galacticfog/gestalt-laser-executor-js:release-",
    ResourceIds.NodeJsExecutor    -> "galacticfog/gestalt-laser-executor-nodejs:release-",
    ResourceIds.PythonExecutor    -> "galacticfog/gestalt-laser-executor-python:release-",
    ResourceIds.RubyExecutor      -> "galacticfog/gestalt-laser-executor-ruby:release-",
    ResourceIds.CsharpExecutor    -> "galacticfog/gestalt-laser-executor-dotnet:release-"
  ) map simpleProviderUpgrade

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
      val fBaseServices = Future.traverse(Seq("security", "meta", "ui-react")) (
        svc => caasClient.flatMap(_.getCurrentImage(svc)).map(svc -> _)
      )

      val plan = for {
        // BASE
        baseSvcs <- fBaseServices
        base = baseSvcs flatMap {
          case (svc,actual) => baseUpgrades.get(svc) map {
            case (exp, tgt) => UpgradeBaseService(svc, exp, tgt, actual)
          }
        }
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
      } yield UpgradePlan(
        Seq(BackupDatabase) ++ base ++ updateExecs ++ updatedProviders
      )

      plan pipeTo sender()
  }
}
