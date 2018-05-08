package com.galacticfog.gestalt

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.ws.WSClient

import scala.concurrent.Future

trait CaasClient {
  def getCurrentImage(serviceName: String): Future[String]
}

trait CaasClientFactory {
  def getClient: CaasClient
}

@Singleton
class DefaultCaasClientFactory @Inject() (ws: WSClient, config: Configuration) extends CaasClientFactory {
  override def getClient: CaasClient = new DefaultCaasClient(ws, config)
}

class DefaultCaasClient(ws: WSClient, config: Configuration) extends CaasClient {
  // TODO
  override def getCurrentImage(serviceName: String): Future[String] = Future.failed(new NotImplementedError)
}
