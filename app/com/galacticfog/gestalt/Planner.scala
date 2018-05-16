package com.galacticfog.gestalt

import java.util.UUID

import akka.pattern.ask
import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.pattern.pipe
import com.galacticfog.gestalt.caas.{CaasClient, CaasClientFactory}
import javax.inject.{Inject, Named}

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.language.implicitConversions

object Planner {
  final val actorName = "planner"

  case object ComputePlan
  case object GetPlan
  case class UpgradePlan(steps: Seq[UpgradeStep])
}

class Planner16 @Inject() ( @Named(CaasClientFactory.actorName) caasClientFactory: ActorRef,
                            metaClient: MetaClient ) extends Actor with ActorLogging {

  import Planner._

  val expectedVersion = "1.5.0"
  val targetVersion   = "1.6.0"

  implicit val ec = context.dispatcher

  def simpleProviderUpgrade(providerAndBaseImage: (UUID, String)): (UUID, (MetaProviderProto, MetaProviderProto)) =
    providerAndBaseImage._1 -> (
      MetaProviderProto(providerAndBaseImage._2 + expectedVersion) , MetaProviderProto(providerAndBaseImage._2 + targetVersion)
    )

  def simpleSvcUpgrade(svcAndBaseImage: (BaseService, String)): (BaseService, (String, String)) =
    svcAndBaseImage._1 -> (
      svcAndBaseImage._2 + expectedVersion, svcAndBaseImage._2 + targetVersion
    )

  val baseUpgrades = Map(
    SECURITY -> "galacticfog/gestalt-security:release-",
    META     -> "galacticfog/gestalt-meta:release-",
    UI       -> "galacticfog/gestalt-ui-react:release-"
  ) map simpleSvcUpgrade

  val providerUpgrades = Map(
    ResourceIds.KongGateway       -> "galacticfog/kong:release-",
    ResourceIds.LambdaProvider    -> "galacticfog/gestalt-laser:release-",
    ResourceIds.GatewayManager    -> "galacticfog/gestalt-api-gateway:release-",
    ResourceIds.PolicyProvider    -> "galacticfog/gestalt-policy:release-",
    ResourceIds.LoggingProvider   -> "galacticfog/gestalt-logger:release-",

    ResourceIds.GoLangExecutor    -> "galacticfog/gestalt-laser-executor-golang:release-",
    ResourceIds.JavaExecutor      -> "galacticfog/gestalt-laser-executor-jvm:release-",
    ResourceIds.NashornExecutor   -> "galacticfog/gestalt-laser-executor-js:release-",
    ResourceIds.NodeJsExecutor    -> "galacticfog/gestalt-laser-executor-nodejs:release-",
    ResourceIds.PythonExecutor    -> "galacticfog/gestalt-laser-executor-python:release-",
    ResourceIds.RubyExecutor      -> "galacticfog/gestalt-laser-executor-ruby:release-",
    ResourceIds.CsharpExecutor    -> "galacticfog/gestalt-laser-executor-dotnet:release-"
  ) map simpleProviderUpgrade

  val metaMigrations = Seq(
    MetaMigration("V1"),
    MetaMigration("V2"),
    MetaMigration("V3")
  )

  override def receive: Receive = {
    case ComputePlan =>
      val caasClient = caasClientFactory.ask(CaasClientFactory.GetClient)(30 seconds).mapTo[CaasClient]

      log.info("received ComputePlan, beginning plan computation...")
      val fBaseServices = Future.traverse(Seq(SECURITY, META, UI)) (
        svc => caasClient.flatMap(_.getCurrentImage(svc)).map(svc -> _)
      )

      val plan = for {
        // Base services
        baseSvcs <- fBaseServices
        base = baseSvcs flatMap {
          case (svc,actual) => baseUpgrades.get(svc) map {
            case (exp, tgt) => UpgradeBaseService(svc, exp, tgt, actual)
          }
        }
        // Providers
        providers <- metaClient.listProviders
        (execs, provs) = providers.partition(p => executorProviders.contains(p.providerType))
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
        Seq(BackupDatabase) ++ base ++ metaMigrations ++ updateExecs ++ updatedProviders
      )

      plan pipeTo sender()
  }
}
