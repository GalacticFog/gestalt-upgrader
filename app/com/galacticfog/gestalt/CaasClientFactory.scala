package com.galacticfog.gestalt

import javax.inject.{Inject, Singleton}

import scala.concurrent.Future

trait CaasClient {
  def getCurrentImage(serviceName: String): Future[String]
}

trait CaasClientFactory {
  def getClient: CaasClient
}

@Singleton
class DefaultCaasClientFactory @Inject() () extends CaasClientFactory {
  override def getClient: CaasClient = ???
}
