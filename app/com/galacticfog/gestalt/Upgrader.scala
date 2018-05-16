package com.galacticfog.gestalt

import akka.persistence.RecoveryCompleted
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import com.galacticfog.gestalt.Upgrader._
import javax.inject.Inject

import scala.reflect.{ClassTag, classTag}
import scala.util.{Success, Failure}

object Upgrader {
  final val actorName = "upgrader"

  sealed trait Command
  case class StartUpgrade(plan: Seq[UpgradeStep]) extends Command
  case object GetLog extends Command

  sealed trait UpgraderState extends FSMState
  case object Stopped extends UpgraderState {
    override def identifier: String = "Stopped"
  }
  case object Upgrading extends UpgraderState {
    override def identifier: String = "Upgrading"
  }
  case object RollingBack extends UpgraderState {
    override def identifier: String = "RollingBack"
  }
  case object Complete extends UpgraderState {
    override def identifier: String = "Complete"
  }
  case object Failed extends UpgraderState {
    override def identifier: String = "Failed"
  }

  sealed trait UpgraderEvent
  case class UpgradeStarted(plan: Seq[UpgradeStep]) extends UpgraderEvent
  case object NextStepStarted extends UpgraderEvent
  case class CurrentStepCompleted(msg: String) extends UpgraderEvent
  case class CurrentStepFailed(err: Throwable) extends UpgraderEvent
  case object UpgradeComplete extends UpgraderEvent

  case class UpgraderData( completedSteps: Seq[UpgradeStep],
                           remainingSteps: Seq[UpgradeStep],
                           currentStep: Option[UpgradeStep],
                           failedStep: Option[(UpgradeStep, Throwable)],
                           log: Seq[String] )
}

class Upgrader @Inject()(executor: Executor)
  extends PersistentFSM[UpgraderState,UpgraderData,UpgraderEvent] {

  override def domainEventClassTag: ClassTag[UpgraderEvent] = classTag[UpgraderEvent]

  override def persistenceId: String = "upgrade-actor"

  implicit val ec = context.dispatcher

  private[this] def dispatchStep(step: UpgradeStep): Unit = {
    log.info(s"dispatching $step to executor")
    executor.execute(step) onComplete({
      case Success(msg) => self ! CurrentStepCompleted(msg)
      case Failure(err) => self ! CurrentStepFailed(err)
    })
  }

  override def applyEvent(event: UpgraderEvent, data: UpgraderData): UpgraderData = event match {
    case UpgradeComplete =>
      if (data.remainingSteps.nonEmpty) {
        log.warning("applying UpgradeComplete even though there are remaining steps")
      }
      if (data.currentStep.isDefined) {
        log.warning("applying UpgradeComplete even though there is a current active step")
      }
      context.system.eventStream.publish(UpgradeComplete)
      log.info("upgrade complete")
      data.copy(
        currentStep = None,
        remainingSteps = Seq.empty,
        log = data.log :+ "Upgrade complete"
      )
    case NextStepStarted =>
      data.remainingSteps.headOption match {
        case None =>
          log.warning("applying NextStepStarted even though there is no next step")
          data.copy(
            currentStep = None
          )
        case Some(nextStep) =>
          log.info(s"marking step active: $nextStep")
          if (recoveryFinished) dispatchStep(nextStep)
          data.copy(
            currentStep = Some(nextStep),
            remainingSteps = data.remainingSteps.tail
          )
      }
    case CurrentStepCompleted(msg) =>
      data.currentStep match {
        case None =>
          log.warning("applying CurrentStepComplete even though there is no current step")
          data
        case Some(current) =>
          log.info(s"marking step complete: $current")
          data.copy(
            completedSteps = data.completedSteps :+ current,
            currentStep = None,
            log = data.log :+ msg
          )
      }
    case CurrentStepFailed(err) =>
      data.currentStep match {
        case None =>
          log.warning("applying CurrentStepFailed even though there is no current step")
          data
        case Some(current) =>
          log.info(s"marking step failed: $current")
          context.system.eventStream.publish(CurrentStepFailed(err))
          data.copy(
            failedStep = Some(current -> err),
            currentStep = None,
            log = data.log :+ err.getMessage
          )
      }
    case UpgradeStarted(plan) =>
      log.info(s"recording upgrade plan with ${plan.size} steps")
      context.system.eventStream.publish(UpgradeStarted(plan))
      UpgraderData(
        completedSteps = Seq.empty,
        remainingSteps = plan,
        currentStep = None,
        failedStep = None,
        log = Seq("Upgrade started")
      )
  }

  startWith(Stopped, UpgraderData(Seq.empty, Seq.empty, None, None, Seq.empty))

  when(Stopped) {
    case Event(StartUpgrade(plan), _) =>
      if (plan.isEmpty) {
        goto(Complete) applying UpgradeStarted(plan) applying UpgradeComplete
      }
      else {
        self ! NextStepStarted
        goto(Upgrading) applying UpgradeStarted(plan)
      }
    case Event(GetLog, d) =>
      stay replying d.log
    case Event(e, _) =>
      log.info("ignoring unexpected command: " + e.toString)
      stay
  }

  when(Upgrading) {
    case Event(RecoveryCompleted, d) =>
      log.info("recovery complete")
      d.currentStep match {
        case None =>
          goto(Complete) applying UpgradeComplete
        case Some(step) =>
          dispatchStep(step)
          stay
      }
    case Event(GetLog, d) =>
      stay replying d.log
    case Event(NextStepStarted, _) =>
      stay applying NextStepStarted
    case Event(complete: CurrentStepCompleted, d) =>
      if (d.remainingSteps.isEmpty) {
        self ! UpgradeComplete
        goto(Complete) applying complete
      }
      else {
        self ! NextStepStarted
        stay applying complete
      }
    case Event(failure: CurrentStepFailed, _) =>
      goto(Failed) applying failure
    case Event(e, _) =>
      log.info("ignoring unexpected command: " + e.toString)
      stay
  }

  when(Complete) {
    case Event(UpgradeComplete, _) =>
      stay applying UpgradeComplete
    case Event(GetLog, d) =>
      stay replying d.log
    case Event(e, _) =>
      log.info("ignoring unexpected command: " + e.toString)
      stay
  }

  when(Failed) {
    case Event(GetLog, d) =>
      stay replying d.log
    case Event(e, _) =>
      log.info("ignoring unexpected command: " + e.toString)
      stay
  }

}
