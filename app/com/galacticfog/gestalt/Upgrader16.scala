package com.galacticfog.gestalt

import akka.actor.ActorRef
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import javax.inject.Inject
import com.galacticfog.gestalt.Upgrader._
import com.google.inject.name.Named

import scala.reflect.ClassTag

trait Upgrader

object Upgrader {
  val actorName = "upgrader"

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

class Upgrader16 @Inject()(@Named("executor") executor: ActorRef)
                          (implicit val domainEventClassTag: ClassTag[UpgraderEvent])
  extends PersistentFSM[UpgraderState,UpgraderData,UpgraderEvent] with Upgrader {

  override def persistenceId: String = "upgrade-1-6-actor"

  override def applyEvent(domainEvent: UpgraderEvent, currentData: UpgraderData): UpgraderData = ???

}
