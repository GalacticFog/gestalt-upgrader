package com.galacticfog.gestalt

import akka.persistence.PersistentActor
import javax.inject.Inject
import play.api.libs.json.{Format, Json}

class UpgradeActor @Inject() () extends PersistentActor {

  import UpgradeActor._

  override def receiveRecover: Receive = {
    case e => println(e)
  }

  override def receiveCommand: Receive = {
    case GetStatus => sender() ! Status(false, false, false, false, false, false)
    case ComputePlan => sender() ! true
    case StartUpgrade => sender() ! true
    case StopUpgrade => sender() ! true
  }

  override def persistenceId: String = "upgrade-actor"

}

object UpgradeActor {

  case object GetStatus
  case object ComputePlan
  case object StartUpgrade
  case object StopUpgrade

  case class Status( hasPlan: Boolean,
                     hasDB: Boolean,
                     planWarnings: Boolean,
                     isRunning: Boolean,
                     isComplete: Boolean,
                     isFailed: Boolean )

  implicit val statusFmt: Format[Status] = Json.format[Status]

}
