package controllers

import akka.actor.ActorRef
import akka.pattern.ask
import com.galacticfog.gestalt.UpgradeActor
import javax.inject._
import play.api._
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

@Singleton
class ApiController @Inject()( cc: ControllerComponents,
                               @Named("upgrade-actor") upgradeActor: ActorRef )
  extends AbstractController(cc) {

  implicit val askTimeout: akka.util.Timeout = 30 seconds
  import UpgradeActor.statusFmt
  implicit val ec = cc.executionContext

  def getStatus() = Action.async {
    val s = (upgradeActor ? UpgradeActor.GetStatus).mapTo[UpgradeActor.Status].map(s => Ok(Json.toJson(s)))
    s transform handleError
  }

  def computePlan() = Action.async {
    val r = (upgradeActor ? UpgradeActor.ComputePlan).map(_ => Accepted)
    r transform handleError
  }

  def getPlan() = play.mvc.Results.TODO

  def getLog(debug: Boolean) = play.mvc.Results.TODO

  def startUpgrade(permissive: Boolean) = Action.async {
    val r = (upgradeActor ? UpgradeActor.StartUpgrade).map(_ => Accepted)
    r transform handleError
  }

  def stopUpgrade(rollback: Boolean) = Action.async {
    val r = (upgradeActor ? UpgradeActor.StopUpgrade).map(_ => Accepted)
    r transform handleError
  }

  def rollback() = play.mvc.Results.TODO

  def handleError(t: Try[Result]) = t match {
    case Success(t) => Success(t)
    case Failure(e) => Success(InternalServerError(Json.obj(
      "error" -> e.toString
    )))
  }

}
