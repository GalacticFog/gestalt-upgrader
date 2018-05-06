package controllers

import akka.actor.ActorRef
import akka.pattern.ask
import com.galacticfog.gestalt.UpgradeManager
import javax.inject._
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.duration._

@Singleton
class ApiController @Inject()( cc: ControllerComponents,
                               @Named(UpgradeManager.actorName) upgradeManager: ActorRef )
  extends AbstractController(cc) {

  implicit val askTimeout: akka.util.Timeout = 30 seconds
  import UpgradeManager.statusFmt
  implicit val ec = cc.executionContext

  def getPlan() = Action.async {
    (upgradeManager ? UpgradeManager.GetPlan).mapTo[Seq[String]].map(s => Ok(Json.toJson(s)))
  }

  def getLog(debug: Boolean) = Action.async {
    (upgradeManager ? UpgradeManager.GetLog).mapTo[Seq[String]].map(s => Ok(Json.toJson(s)))
  }

  def getStatus() = Action.async {
    (upgradeManager ? UpgradeManager.GetStatus).mapTo[UpgradeManager.Status].map(s => Ok(Json.toJson(s)))
  }

  def computePlan() = Action.async {
    (upgradeManager ? UpgradeManager.ComputePlan).map(_ => Accepted)
  }

  def startUpgrade(permissive: Boolean) = Action.async {
     (upgradeManager ? UpgradeManager.StartUpgrade(permissive)).map(_ => Accepted)
  }

  def stopUpgrade(rollback: Boolean) = Action.async {
    (upgradeManager ? UpgradeManager.StopUpgrade(rollback)).map(_ => Accepted)
  }

  def rollback() = Action.async {
    (upgradeManager ? UpgradeManager.Rollback).map(_ => Accepted)
  }

}
