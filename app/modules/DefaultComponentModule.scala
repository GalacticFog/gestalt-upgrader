package modules

import akka.stream.Materializer
import com.galacticfog.gestalt._
import com.galacticfog.gestalt.caas.dcos.DCOSAuthTokenActor
import com.typesafe.sslconfig.ssl.{SSLConfigSettings, SSLLooseConfig}
import javax.inject.{Inject, Singleton}
import net.codingwell.scalaguice.ScalaModule
import play.api.Configuration
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.libs.ws.{WSClient, WSClientConfig}
import play.api.libs.ws.ahc.{AhcWSClient, AhcWSClientConfig}

class DefaultComponentModule extends ScalaModule with AkkaGuiceSupport {

  override def configure(): Unit = {
    bindActor[UpgradeManager](UpgradeManager.actorName)
    bindActor[Upgrader16](Upgrader.actorName)
    bindActor[Planner16](Planner.actorName)
    bindActor[Executor](Executor.actorName)
    bindActor[DCOSAuthTokenActor](DCOSAuthTokenActor.actorName)
    bind[WSClientFactory].to[DefaultWSClientFactory]
    bind[CaasClientFactory].to[DefaultCaasClientFactory]
    bind[MetaClient].to[DefaultMetaClient]
  }

}

trait WSClientFactory {
  def getClient(permissive: Boolean): WSClient
}

@Singleton
class DefaultWSClientFactory @Inject()( config: Configuration, defaultClient: WSClient )
                                      ( implicit val mat: Materializer ) extends WSClientFactory {

  private lazy val permissiveClient: AhcWSClient = {
    AhcWSClient(AhcWSClientConfig(WSClientConfig(
      ssl = SSLConfigSettings().withLoose(SSLLooseConfig().withAcceptAnyCertificate(true))
    )))
  }

  def getClient(permissive: Boolean): WSClient = {
    if (permissive) permissiveClient
    else defaultClient
  }

}
