package com.galacticfog.gestalt

import javax.inject.Inject
import play.api.Configuration
import play.api.libs.ws.WSClient

import scala.concurrent.Future

trait MetaClient {
  def listProviders: Future[Seq[MetaProvider]]
}

class DefaultMetaClient @Inject() (ws: WSClient, config: Configuration) extends MetaClient {
  override def listProviders: Future[Seq[MetaProvider]] = ???
}
