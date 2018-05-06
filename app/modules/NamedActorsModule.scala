package modules

import com.galacticfog.gestalt._
import net.codingwell.scalaguice.ScalaModule
import play.api.libs.concurrent.AkkaGuiceSupport

class NamedActorsModule extends ScalaModule with AkkaGuiceSupport {

  override def configure(): Unit = {
    bindActor[UpgradeManager](UpgradeManager.actorName),
    bindActor[Upgrader16](Upgrader.actorName),
    bindActor[Planner16](Planner.actorName)
  }

}
