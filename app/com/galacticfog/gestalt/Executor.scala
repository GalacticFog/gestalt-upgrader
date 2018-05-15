package com.galacticfog.gestalt

import akka.actor.ActorRef
import com.galacticfog.gestalt.caas.{CaasClient, CaasClientFactory}

import scala.concurrent.{ExecutionContext, Future}
import javax.inject.{Inject, Named, Singleton}

import scala.concurrent.duration._
import akka.pattern.ask

trait Executor {
  def execute(step: UpgradeStep): Future[String]
  def revert(step: UpgradeStep): Future[String]
}

@Singleton
class DefaultExecutor @Inject() ( metaClient: MetaClient,
                                  @Named(CaasClientFactory.actorName) caasClientFactory: ActorRef )
                                ( implicit ec: ExecutionContext ) extends Executor {
  override def execute(step: UpgradeStep): Future[String] = step match {
    case BackupDatabase =>
      Future.successful("database backup not yet supported")
    case UpgradeProvider(_, tgt, planned) =>
      for {
        actual <- metaClient.getProvider(planned.fqon, planned.id)
        _ <- if (planned.image == actual.image) Future.successful(()) else Future.failed(
          new RuntimeException("provider was different than in computed plan; please recompute plan and try again")
        )
        updated <- metaClient.updateProvider(planned.copy(
          image = Some(tgt.image)
        ))
        if (updated.image.contains(tgt.image))
      } yield s"upgraded meta provider ${updated.name} (${updated.id}) from ${planned.image.get} to ${updated.image.get}"
    case UpgradeExecutor(_, tgt, planned) =>
      for {
        actual <- metaClient.getProvider(planned.fqon, planned.id)
        _ <- if (planned.image == actual.image) Future.successful(()) else Future.failed(
          new RuntimeException("executor was different than in computed plan; please recompute plan and try again")
        )
        updated <- metaClient.updateProvider(planned.copy(
          image = Some(tgt.image)
        ))
        if (updated.image.contains(tgt.image))
      } yield s"upgraded meta executor ${updated.name} (${updated.id}) from ${planned.image.get} to ${updated.image.get}"
    case UpgradeBaseService(svcName, _, tgtImg, plannedImg) =>
      for {
        caasClient <- caasClientFactory.ask(CaasClientFactory.GetClient)(30 seconds).mapTo[CaasClient]
        updated <- caasClient.updateImage(svcName, tgtImg, Some(plannedImg))
      } yield s"upgraded base service '$svcName' from $plannedImg to $tgtImg"
  }

  override def revert(step: UpgradeStep): Future[String] = step match {
    case BackupDatabase =>
      Future.successful("database backup not yet supported")
    case UpgradeProvider(_, _, planned) =>
      for {
        updated <- metaClient.updateProvider(planned)
        if (updated.image.get == planned.image.get)
      } yield s"reverted meta provider ${updated.name} (${updated.id}) to ${updated.image.get}"
    case UpgradeExecutor(_, _, planned) =>
      for {
        updated <- metaClient.updateProvider(planned)
        if (updated.image.get == planned.image.get)
      } yield s"reverted meta executor ${updated.name} (${updated.id}) to ${updated.image.get}"
    case UpgradeBaseService(svcName, _, tgtImg, plannedImg) =>
      for {
        caasClient <- caasClientFactory.ask(CaasClientFactory.GetClient)(30 seconds).mapTo[CaasClient]
        updated <- caasClient.updateImage(svcName, plannedImg, None)
        if (updated == plannedImg)
      } yield s"reverted base service '$svcName' to $plannedImg"
  }
}
