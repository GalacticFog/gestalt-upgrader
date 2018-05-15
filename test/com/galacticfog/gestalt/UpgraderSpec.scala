package com.galacticfog.gestalt

import akka.actor.{ActorRef, ActorSystem}
import akka.persistence.fsm.PersistentFSM.{CurrentState, SubscribeTransitionCallBack, Transition}
import akka.testkit.{ImplicitSender, TestFSMRef, TestKit, TestProbe}
import com.galacticfog.gestalt.Upgrader.{UpgraderData, UpgraderState}
import com.galacticfog.gestalt.caas.{CaasClient, CaasClientFactory}
import com.google.inject.AbstractModule
import com.google.inject.name.Names
import modules.DefaultComponentModule
import net.codingwell.scalaguice.ScalaModule
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.{BindingKey, Injector, bind}
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.test.Helpers._

import scala.concurrent.Future

class UpgraderSpec extends Specification with Mockito {

  "Planner16" should {

    def uuid = java.util.UUID.randomUUID()

    case class TestModule(executor: Executor) extends AbstractModule with ScalaModule with AkkaGuiceSupport {
      override def configure(): Unit = {
        bind[Executor].toInstance(executor)
        bindActor[Upgrader](Upgrader.actorName)
      }
    }

    abstract class WithConfig(config: (String,Any)*)
      extends TestKit(ActorSystem("launcher-test-system")) with Scope with ImplicitSender {

      val mockExecutor = mock[Executor]

      val injector =
        new GuiceApplicationBuilder()
          .disable[modules.DefaultComponentModule]
          .bindings(TestModule(
            executor = mockExecutor
          ))
          .configure(config:_*)
          .injector
    }

    "execute all upgrade steps in plan" in new WithConfig() {
      val upgraderRef: ActorRef = injector.instanceOf(BindingKey(classOf[ActorRef]).qualifiedWith(Upgrader.actorName))

      val plan = Seq(
        BackupDatabase,
        UpgradeBaseService(SECURITY, "security:expected", "security:target", "security:actual"),
        UpgradeExecutor(MetaProviderProto("executor:expected"), MetaProviderProto("executor:target"), MetaProvider(
          "root", "executor", uuid, ResourceIds.JavaExecutor, Some("executor:actual")
        )),
        UpgradeProvider(MetaProviderProto("laser:expected"), MetaProviderProto("laser:target"), MetaProvider(
          "root", "laser", uuid, ResourceIds.LambdaProvider, Some("laser:actual")
        ))
      )

      upgraderRef ! SubscribeTransitionCallBack(testActor)
      expectMsg(CurrentState(upgraderRef, Upgrader.Stopped, None))

      upgraderRef ! Upgrader.StartUpgrade(plan)
      expectMsg(Transition(upgraderRef, Upgrader.Stopped, Upgrader.Running, None))
    }

  }

}
