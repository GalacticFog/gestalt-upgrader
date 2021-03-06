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

  def getCaasClient = caasClientFactory.ask(CaasClientFactory.GetClient)(30 seconds).mapTo[CaasClient]

  override def execute(step: UpgradeStep): Future[String] = step match {
    case ResumeBaseService(svc) =>
      for {
        caasClient <- getCaasClient
        newSvc <- caasClient.scale(svc, svc.numInstances)
        if (newSvc.numInstances == svc.numInstances)
      } yield s"Resumed base service '${svc.name}' to ${newSvc.numInstances} instances"
    case SuspendBaseService(svc) =>
      for {
        caasClient <- getCaasClient
        newSvc <- caasClient.scale(svc, 0)
        if (newSvc.numInstances == 0)
      } yield s"Suspended base service '${svc.name}' to ${newSvc.numInstances} instances"
    case BackupDatabase =>
      Future.successful("NOTE: Database backup not yet supported")
    case UpgradeProvider(_, tgt, planned) =>
      for {
        actual <- metaClient.getProvider(planned.fqon, planned.id)
        _ <- if (actual.getProto == planned.getProto) Future.successful(()) else Future.failed(
          new RuntimeException("ERROR: Provider was different than in computed plan; please recompute plan and try again")
        )
        updated <- metaClient.updateProvider(actual, tgt)
        if (updated.getProto == tgt)
      } yield s"Upgraded meta provider ${updated.name} (${updated.id}) from ${planned.getProto} to ${updated.getProto}"
    case UpgradeExecutor(_, tgt, planned) =>
      for {
        actual <- metaClient.getProvider(planned.fqon, planned.id)
        _ <- if (planned.getProto == actual.getProto) Future.successful(()) else Future.failed(
          new RuntimeException("ERROR: Executor was different than in computed plan; please recompute plan and try again")
        )
        updated <- metaClient.updateProvider(actual, tgt)
        if (updated.getProto == tgt)
      } yield s"Upgraded meta executor ${updated.name} (${updated.id}) from ${planned.getProto} to ${updated.getProto}"
    case UpgradeBaseService(_, target, planned) =>
      for {
        caasClient <- getCaasClient
        actual <- caasClient.getService(planned.name)
        _ <- if (actual.getProto == planned.getProto) Future.successful(()) else Future.failed(
          new RuntimeException("ERROR: Base service was different than in computed plan; please recompute plan and try again")
        )
        updated <- caasClient.update(actual, target)
        if (updated.getProto == target)
      } yield s"Upgraded base service '${actual.name}' from ${actual.getProto} to ${updated.getProto}"
    case MetaMigration(version) =>
      metaClient.performMigration(version)
  }

  override def revert(step: UpgradeStep): Future[String] = step match {
    case ResumeBaseService(svc) =>
      execute(SuspendBaseService(svc))
    case SuspendBaseService(svc) =>
      execute(ResumeBaseService(svc))
    case BackupDatabase =>
      Future.successful("NOTE: Database backup not yet supported")
    case UpgradeProvider(_, _, planned) =>
      for {
        updated <- metaClient.updateProvider(planned, planned.getProto)
        if (updated.getProto == planned.getProto)
      } yield s"Reverted meta provider ${updated.name} (${updated.id}) to ${updated.getProto}"
    case UpgradeExecutor(_, _, planned) =>
      for {
        updated <- metaClient.updateProvider(planned, planned.getProto)
        if (updated.getProto == planned.getProto)
      } yield s"Reverted meta executor ${updated.name} (${updated.id}) to ${updated.getProto}"
    case UpgradeBaseService(_, _, planned) =>
      for {
        caasClient <- getCaasClient
        updated <- caasClient.update(planned, planned.getProto)
        if (updated.getProto == planned.getProto)
      } yield s"Reverted base service '${planned.name}' to ${planned.getProto}"
    case MetaMigration(_) =>
      Future.successful("Meta migrations cannot be reverted; database restore will handle this")
  }

}
