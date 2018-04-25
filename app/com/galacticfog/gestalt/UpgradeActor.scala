package com.galacticfog.gestalt

import akka.actor.Status.Failure
import akka.persistence.PersistentActor
import javax.inject.Inject
import play.api.libs.json.{Format, Json}

class UpgradeActor @Inject() () extends PersistentActor {

  import UpgradeActor._

  var currentState = Status(
    hasPlan = false,
    hasDB = false,
    planWarnings = false,
    isRunning = false,
    isComplete = false,
    isFailed = false
  )

  var currentPlan: String = _

  var currentLog: String = _

  override def receiveRecover: Receive = {
    case evt: Event => evt match {
      case e: UpdatePlan => updatePlan(e)
      case e: CompletionEvent => updateStatus(e)
    }
    case e => println(e)
  }

  override def receiveCommand: Receive = {
    case GetStatus => sender() ! currentState
    case StartUpgrade(permissive) =>
      if (currentState.isRunning)
        sender() ! Failure(new BadRequestException("upgrade is already running"))
      else if (currentState.isComplete)
        sender() ! Failure(new BadRequestException("upgrade has already successfully completed"))
      else if (currentState.isFailed)
        sender() ! Failure(new BadRequestException("upgrade has already unsuccessfully completed"))
      else if (!currentState.hasPlan)
        sender() ! Failure(new BadRequestException("plan does not exist; compute plan first"))
      else if (currentState.planWarnings && !permissive)
        sender() ! Failure(new BadRequestException("plan has warnings; specify ?permissive=true to override"))
      else {
        sender() ! true
        persist(UpgradeStarted) {
          evt => updateStatus(evt)
        }
      }
    case StopUpgrade(rollback) =>
      sender() ! Failure(new NotImplementedError)
      // sender() ! true
    case Rollback =>
      sender() ! Failure(new NotImplementedError)
      // sender() ! true
    case GetPlan =>
      if (currentState.hasPlan) {
        sender() ! currentPlan
      } else {
        sender() ! Failure(new BadRequestException("plan does not exist; compute plan first"))
      }
    case GetLog =>
      if (currentState.isComplete || currentState.isFailed || currentState.isRunning) {
        sender() ! currentLog
      } else {
        sender() ! Failure(new BadRequestException("log does not exist; start upgrade first"))
      }
    case ComputePlan =>
      sender() ! true
      persist(UpdatePlan(
        """Backup database
          |Upgrade core service gestalt-security from galacticfog/gestalt-security:release-1.5.0 to galacticfog/gestalt-security:release-1.6.0
          |Upgrade core service gestalt-meta from galacticfog/gestalt-meta:release-1.5.0 to galacticfog/gestalt-meta:release-1.6.0
          |Upgrade core service UI from galacticfog/gestalt-ui-react:release-1.5.0 to galacticfog/gestalt-ui-react:release-1.6.0
          |Migrate meta schema: V2 -> V4
          |Ugprade provider nodejs-executor from galacticfog/gestalt-laser-executor-nodejs:release-1.5.0 to galacticfog/gestalt-laser-executor-nodejs:release-1.6.0
          |WARNING: provider nashorn-executor using galacticfog/gestalt-laser-executor-nashorn:release-1.5.1-custom
          |Upgrade provider nashorn-executor from galacticfog/gestalt-laser-executor-nashorn:release-1.5.1-custom to galacticfog/gestalt-laser-executor-nashorn:release-1.6.0
          |Upgrade provider lsr from galacticfog/gestalt-laser:release-1.5.0 to galacticfog/gestalt-laser:release-1.6.0
          |Upgrade provider kong from galacticfog/gestalt-kong:release-1.5.0 to galacticfog/gestalt-laser:release-1.6.0
        """.stripMargin, true)) {
        evt => updatePlan(evt)
      }
  }

  override def persistenceId: String = "upgrade-actor"

  private[this] def updatePlan(e: UpdatePlan): Unit = {
    currentPlan = e.plan
    currentState = currentState.copy(
      hasPlan = true,
      planWarnings = e.hasWarnings
    )
  }

  private[this] def updateStatus(e: CompletionEvent) = e match {
    case UpgradeStarted =>
      currentState = currentState.copy(
        isRunning = true,
        isComplete = false,
        isFailed = false
      )
      currentLog =
        """Backing up database...
          |Backup complete.
          |Backup available from https://gtw1.galactic-equity.com/upgrade/7401e17a-ed9a-447c-941c-893ed4d40ca5/database.tgz
          |Upgrading security from galacticfog/gestalt-security:release-1.5.0 to galacticfog/gestalt-security:release-1.6.0
          |Security upgraded.
          |Security healthy.
          |Upgrading meta from galacticfog/gestalt-meta:release-1.5.0 to galacticfog/gestalt-meta:release-1.6.0
        """.stripMargin
    case UpgradeCompleted =>
      currentState = currentState.copy(
        isRunning = false,
        isComplete = true,
        isFailed = false
      )
    case UpgradeFailed =>
      currentState = currentState.copy(
        isRunning = false,
        isComplete = false,
        isFailed = true
      )
  }

}

object UpgradeActor {

  case class BadRequestException(msg: String) extends RuntimeException(msg)

  case object GetStatus
  case object GetPlan
  case object GetLog
  case object ComputePlan
  case class StartUpgrade(permissive: Boolean)
  case class StopUpgrade(rollback: Boolean)
  case object Rollback

  sealed trait Event
  sealed trait CompletionEvent extends Event
  case class UpdatePlan(plan: String, hasWarnings: Boolean) extends Event

  case object UpgradeStarted extends CompletionEvent
  case object UpgradeFailed extends CompletionEvent
  case object UpgradeCompleted extends CompletionEvent

  case class Status( hasPlan: Boolean,
                     hasDB: Boolean,
                     planWarnings: Boolean,
                     isRunning: Boolean,
                     isComplete: Boolean,
                     isFailed: Boolean )

  implicit val statusFmt: Format[Status] = Json.format[Status]

}
