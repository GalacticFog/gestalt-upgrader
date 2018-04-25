package modules

import com.galacticfog.gestalt.UpgradeActor
import net.codingwell.scalaguice.ScalaModule
import play.api.libs.concurrent.AkkaGuiceSupport

class NamedActorsModule extends ScalaModule with AkkaGuiceSupport {

  override def configure(): Unit = {
    bindActor[UpgradeActor]("upgrade-actor")
  }

}
