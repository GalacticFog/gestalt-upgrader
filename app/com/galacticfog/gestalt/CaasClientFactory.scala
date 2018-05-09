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
class DefaultCaasClientFactory @Inject() (ws: WSClient, config: Configuration, meta: MetaClient) extends CaasClientFactory {
  override def getClient: CaasClient = new DefaultCaasClient(ws, config, meta)
}

class DefaultCaasClient(ws: WSClient, config: Configuration, meta: MetaClient) extends CaasClient {
  // TODO
  override def getCurrentImage(serviceName: String): Future[String] = Future.failed(new NotImplementedError)
}
