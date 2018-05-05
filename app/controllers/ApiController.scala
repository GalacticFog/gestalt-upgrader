package controllers

import akka.actor.ActorRef
import akka.pattern.ask
import com.galacticfog.gestalt.UpgradeActor
import javax.inject._
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.duration._

@Singleton
class ApiController @Inject()( cc: ControllerComponents,
                               @Named("upgrade-actor") upgradeActor: ActorRef )
  extends AbstractController(cc) {

  implicit val askTimeout: akka.util.Timeout = 30 seconds
  import UpgradeActor.statusFmt
  implicit val ec = cc.executionContext

  def getPlan() = Action.async {
    (upgradeActor ? UpgradeActor.GetPlan).mapTo[Seq[String]].map(s => Ok(Json.toJson(s)))
  }

  def getLog(debug: Boolean) = Action.async {
    (upgradeActor ? UpgradeActor.GetLog).mapTo[Seq[String]].map(s => Ok(Json.toJson(s)))
  }

  def getStatus() = Action.async {
    (upgradeActor ? UpgradeActor.GetStatus).mapTo[UpgradeActor.Status].map(s => Ok(Json.toJson(s)))
  }

  def computePlan() = Action.async {
    (upgradeActor ? UpgradeActor.ComputePlan).map(_ => Accepted)
  }

  def startUpgrade(permissive: Boolean) = Action.async {
     (upgradeActor ? UpgradeActor.StartUpgrade(permissive)).map(_ => Accepted)
  }

  def stopUpgrade(rollback: Boolean) = Action.async {
    (upgradeActor ? UpgradeActor.StopUpgrade(rollback)).map(_ => Accepted)
  }

  def rollback() = Action.async {
    (upgradeActor ? UpgradeActor.Rollback).map(_ => Accepted)
  }

}
