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
        _ <- if (actual.getProto == planned.getProto) Future.successful(()) else Future.failed(
          new RuntimeException("provider was different than in computed plan; please recompute plan and try again")
        )
        updated <- metaClient.updateProvider(actual, tgt)
        if (updated.image.contains(tgt.image))
      } yield s"upgraded meta provider ${updated.name} (${updated.id}) from ${planned.getProto} to ${updated.getProto}"
    case UpgradeExecutor(_, tgt, planned) =>
      for {
        actual <- metaClient.getProvider(planned.fqon, planned.id)
        _ <- if (planned.getProto == actual.getProto) Future.successful(()) else Future.failed(
          new RuntimeException("executor was different than in computed plan; please recompute plan and try again")
        )
        updated <- metaClient.updateProvider(actual, tgt)
        if (updated.image.contains(tgt.image))
      } yield s"upgraded meta executor ${updated.name} (${updated.id}) from ${planned.getProto} to ${updated.getProto}"
    case UpgradeBaseService(service, _, tgtImg, plannedImg) =>
      for {
        caasClient <- caasClientFactory.ask(CaasClientFactory.GetClient)(30 seconds).mapTo[CaasClient]
        updated <- caasClient.updateImage(service, tgtImg, Seq(plannedImg, tgtImg))
      } yield s"upgraded base service '${service.name}' from $plannedImg to $tgtImg"
    case MetaMigration(version) =>
      // metaClient.performMigration(version).map(_ => "Migration completed")
      Future.successful(s"MetaMigration($version) disabled")
  }

  override def revert(step: UpgradeStep): Future[String] = step match {
    case BackupDatabase =>
      Future.successful("database backup not yet supported")
    case UpgradeProvider(_, _, planned) =>
      for {
        updated <- metaClient.updateProvider(planned, planned.getProto)
        if (updated.getProto == planned.getProto)
      } yield s"reverted meta provider ${updated.name} (${updated.id}) to ${updated.image.get}"
    case UpgradeExecutor(_, _, planned) =>
      for {
        updated <- metaClient.updateProvider(planned, planned.getProto)
        if (updated.getProto == planned.getProto)
      } yield s"reverted meta executor ${updated.name} (${updated.id}) to ${updated.image.get}"
    case UpgradeBaseService(service, _, tgtImg, plannedImg) =>
      for {
        caasClient <- caasClientFactory.ask(CaasClientFactory.GetClient)(30 seconds).mapTo[CaasClient]
        updated <- caasClient.updateImage(service, plannedImg, Seq.empty)
        if (updated == plannedImg)
      } yield s"reverted base service '${service.name}' to $plannedImg"
    case MetaMigration(_) =>
      Future.successful("Meta migrations cannot be reverted; database restore will handle this")
  }
}
