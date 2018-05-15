package modules

import akka.stream.Materializer
import com.galacticfog.gestalt._
import com.galacticfog.gestalt.caas.{CaasClientFactory, DefaultCaasClientFactory}
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
    bindActor[Upgrader](Upgrader.actorName)
    bindActor[Planner16](Planner.actorName)
    bindActor[DCOSAuthTokenActor](DCOSAuthTokenActor.actorName)
    bindActor[DefaultCaasClientFactory](CaasClientFactory.actorName)
    bind[WSClientFactory].to[DefaultWSClientFactory]
    bind[MetaClient].to[DefaultMetaClient]
    bind[Executor].to[DefaultExecutor]
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
