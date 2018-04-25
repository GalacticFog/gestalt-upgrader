package controllers

import com.galacticfog.gestalt.UpgradeActor.BadRequestException
import play.api.http.HttpErrorHandler
import play.api.libs.json.Json
import play.api.mvc._
import play.api.mvc.Results._
import javax.inject.Singleton

import scala.concurrent.Future

@Singleton
class ErrorHandler extends HttpErrorHandler {
  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    Future.successful(
      Status(statusCode)(message)
    )
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    exception match {
      case br: BadRequestException => Future.successful(
        BadRequest(Json.obj(
          "error" -> br.getMessage
        ))
      )
      case t => Future.successful(
        InternalServerError(Json.obj(
          "error" -> t.getMessage
        ))
      )
    }
  }
}
