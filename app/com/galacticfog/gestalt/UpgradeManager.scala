package com.galacticfog.gestalt

import akka.actor.{ActorLogging, ActorRef}
import akka.actor.Status.Failure
import akka.persistence.{PersistentActor, RecoveryCompleted}
import javax.inject.{Inject, Named}
import play.api.libs.json.{Format, Json}

class UpgradeManager @Inject()( @Named(Upgrader.actorName) upgrader: ActorRef,
                                @Named(Planner.actorName) planner: ActorRef ) extends PersistentActor with ActorLogging {

  import UpgradeManager._

  override def persistenceId: String = "upgrade-manager"

  private[this] var currentState = Status(
    hasPlan = false,
    hasDB = false,
    planWarnings = false,
    isRunning = false,
    isComplete = false,
    isFailed = false
  )

  private[this] var currentPlan: Seq[UpgradeStep] = _

  override def preStart = context.system.eventStream.subscribe(self, classOf[Upgrader.UpgraderEvent])

  override def receiveRecover: Receive = {
    case RecoveryCompleted =>
      log.info("recovery completed")
    case Planner.UpgradePlan(steps) =>
      log.info("recovering plan")
      updatePlan(steps)
    case e: CompletionEvent =>
      log.info(s"recovering status $e")
      updateStatus(e)
    case e => println(e)
  }

  override def receiveCommand: Receive = {
    case Upgrader.UpgradeComplete =>
      persist(UpgradeManager.UpgradeCompleted) {
        evt => updateStatus(evt)
      }
    case Upgrader.CurrentStepFailed(_) =>
      persist(UpgradeManager.UpgradeFailed) {
        evt => updateStatus(evt)
      }
    case Upgrader.UpgradeStarted(_) =>
      persist(UpgradeManager.UpgradeStarted) {
        evt => updateStatus(evt)
      }
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
        upgrader ! Upgrader.StartUpgrade(currentPlan)
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
        upgrader.tell(Upgrader.GetLog, sender())
      } else {
        sender() ! Failure(new BadRequestException("log does not exist; start upgrade first"))
      }
    case ComputePlan =>
      sender() ! true
      planner ! Planner.ComputePlan
    case evt: Planner.UpgradePlan =>
      persist(evt) {
        evt => updatePlan(evt.steps)
      }
  }

  private[this] def updatePlan(steps: Seq[UpgradeStep]): Unit = {
    currentPlan = steps
    currentState = currentState.copy(
      hasPlan = true,
      planWarnings = steps.exists(_.warning)
    )
  }

  private[this] def updateStatus(e: CompletionEvent) = e match {
    case UpgradeStarted =>
      currentState = currentState.copy(
        isRunning = true,
        isComplete = false,
        isFailed = false
      )
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

object UpgradeManager {

  final val actorName = "upgrade-manager"

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
