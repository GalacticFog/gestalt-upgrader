package com.galacticfog.gestalt

import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import com.galacticfog.gestalt.Upgrader._
import javax.inject.Inject

import scala.reflect.{ClassTag, classTag}

object Upgrader {
  final val actorName = "upgrader"

  sealed trait UpgraderState extends FSMState
  case object Stopped extends UpgraderState {
    override def identifier: String = "Stopped"
  }
  case object Running extends UpgraderState {
    override def identifier: String = "Running"
  }
  case object Complete extends UpgraderState {
    override def identifier: String = "Complete"
  }
  case object Failed extends UpgraderState {
    override def identifier: String = "Failed"
  }

  sealed trait UpgraderEvent
  case class StartUpgrade(plan: Seq[UpgradeStep])
  case class StepCompleted(s: UpgradeStep) extends UpgraderEvent
  case class StepFailed(s: UpgradeStep, t: Throwable) extends UpgraderEvent
  case class StepStarted(s: UpgradeStep)

  case class UpgraderData( completedSteps: Seq[UpgradeStep],
                           remainingSteps: Seq[UpgradeStep],
                           currentStep: Option[UpgradeStep],
                           failedStep: Option[(UpgradeStep, Throwable)] )
}

class Upgrader @Inject()(executor: Executor)
  extends PersistentFSM[UpgraderState,UpgraderData,UpgraderEvent] {

  override def domainEventClassTag: ClassTag[UpgraderEvent] = classTag[UpgraderEvent]

  override def persistenceId: String = "upgrade-1-6-actor"

  override def applyEvent(event: UpgraderEvent, currentData: UpgraderData): UpgraderData = {
    currentData
  }

  startWith(Stopped, UpgraderData(Seq.empty, Seq.empty, None, None))

  when(Stopped) {
    case Event(e, d) =>
      log.info(e.toString)
      stay
  }

}
