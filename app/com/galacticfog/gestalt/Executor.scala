package com.galacticfog.gestalt

import scala.concurrent.{ExecutionContext, Future}
import javax.inject.{Inject, Singleton}

import scala.util.Try

trait Executor {
  def execute(step: UpgradeStep): Future[String]
  def revert(step: UpgradeStep): Future[String]
}

@Singleton
class DefaultExecutor @Inject() (metaClient: MetaClient)(implicit ec: ExecutionContext) extends Executor {
  override def execute(step: UpgradeStep): Future[String] = step match {
    case BackupDatabase =>
      Future.successful("database backup not yet supported")
    case UpgradeProvider(exp, tgt, planned) =>
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
    case u: UpgradeBaseService =>
      ???
    case u: UpgradeExecutor =>
      ???
  }

  override def revert(step: UpgradeStep): Future[String] = step match {
    case BackupDatabase =>
      Future.successful("database backup not yet supported")
    case UpgradeProvider(exp, tgt, planned) =>
      for {
        updated <- metaClient.updateProvider(planned)
        if (updated.image.get == planned.image.get)
      } yield s"reverted meta provider ${updated.name} (${updated.id}) to ${updated.image.get}"
    case u: UpgradeBaseService =>
      ???
    case u: UpgradeExecutor =>
      ???
  }
}
