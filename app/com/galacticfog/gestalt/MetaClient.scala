package com.galacticfog.gestalt

import javax.inject.{Inject, Singleton}
import play.api.libs.ws.WSClient

import scala.concurrent.Future

trait MetaClient {
  def listProviders: Future[Seq[MetaProvider]]
}

class DefaultMetaClient @Inject() (ws: WSClient) extends MetaClient {
  override def listProviders: Future[Seq[MetaProvider]] = ???
}
