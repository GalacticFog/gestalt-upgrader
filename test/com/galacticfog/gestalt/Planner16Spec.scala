package com.galacticfog.gestalt

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestKit, TestProbe}
import com.galacticfog.gestalt.caas.{CaasClient, CaasClientFactory}
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

class Planner16Spec extends Specification with Mockito {

  "Planner16" should {

    def uuid = java.util.UUID.randomUUID()

    val actorSystem = ActorSystem("test")
    class TestActors extends TestKit(actorSystem) with Scope

    val mockCaasClient = mock[CaasClient]
    val mockMetaClient = mock[MetaClient]
    val testCaasFactory = TestProbe("test-caas-factory")(actorSystem)

    class TestModule extends ScalaModule with AkkaGuiceSupport {
      override def configure(): Unit = {
        bindActor[UpgradeManager](UpgradeManager.actorName)
        bindActor[Planner16](Planner.actorName)
        bindActor[Upgrader](Upgrader.actorName)
        bind[MetaClient].toInstance(mockMetaClient)
        bind[Executor].toInstance(mock[Executor])
        bind(classOf[ActorRef]).annotatedWith(Names.named(CaasClientFactory.actorName)).toInstance(testCaasFactory.ref)
      }
    }

    val app = new GuiceApplicationBuilder(
      disabled = Seq(classOf[DefaultComponentModule]),
      modules = Seq(new TestModule)
    ).overrides(
      bind[ActorSystem].toInstance(actorSystem)
    ).build()

    "plan to upgrade all base services to release-1.6.0" in new TestActors {
      running(app) {
        val injector: Injector = app.injector
        val planner: ActorRef = injector.instanceOf(BindingKey(classOf[ActorRef]).qualifiedWith(Planner.actorName))

        mockCaasClient.getCurrentImage(SECURITY) returns Future.successful("galacticfog/gestalt-security:release-1.5.1")
        mockCaasClient.getCurrentImage(META)     returns Future.successful("galacticfog/gestalt-meta:release-1.5.2")
        mockCaasClient.getCurrentImage(UI) returns Future.successful("galacticfog/gestalt-ui-react:release-1.5.3")

        val currentProviders = Seq(
          MetaProvider("root", "default-kong-provider",      uuid, ResourceIds.KongGateway,    Some("galacticfog/kong:release-1.5.1")),
          MetaProvider("root", "default-laser-executor-jvm", uuid, ResourceIds.JavaExecutor,   Some("galacticfog/gestalt-laser-executor-jvm:release-1.5.2.1")),
          MetaProvider("root", "default-laser-provider",     uuid, ResourceIds.LambdaProvider, Some("galacticfog/gestalt-laser:release-1.5.2")),
          MetaProvider("test", "default-gwm",                uuid, ResourceIds.GatewayManager, Some("galacticfog/gestalt-api-gateway:release-1.5.3"))
        )

        mockMetaClient.listProviders returns Future.successful(currentProviders)

        val testProbe = TestProbe()
        planner.tell(Planner.ComputePlan, testProbe.ref)

        testCaasFactory.expectMsg(CaasClientFactory.GetClient)
        testCaasFactory.reply(mockCaasClient)

        testProbe.expectMsg(Planner.UpgradePlan(Seq(
          BackupDatabase,
          UpgradeBaseService(SECURITY, "galacticfog/gestalt-security:release-1.5.0", "galacticfog/gestalt-security:release-1.6.0", "galacticfog/gestalt-security:release-1.5.1"),
          UpgradeBaseService(META,         "galacticfog/gestalt-meta:release-1.5.0",     "galacticfog/gestalt-meta:release-1.6.0",     "galacticfog/gestalt-meta:release-1.5.2"),
          UpgradeBaseService(UI,       "galacticfog/gestalt-ui-react:release-1.5.0", "galacticfog/gestalt-ui-react:release-1.6.0", "galacticfog/gestalt-ui-react:release-1.5.3"),
          UpgradeExecutor(MetaProviderProto("galacticfog/gestalt-laser-executor-jvm:release-1.5.0"), MetaProviderProto("galacticfog/gestalt-laser-executor-jvm:release-1.6.0"), currentProviders(1)),
          UpgradeProvider(MetaProviderProto("galacticfog/kong:release-1.5.0"),                MetaProviderProto("galacticfog/kong:release-1.6.0"),                currentProviders(0)),
          UpgradeProvider(MetaProviderProto("galacticfog/gestalt-laser:release-1.5.0"),       MetaProviderProto("galacticfog/gestalt-laser:release-1.6.0"),       currentProviders(2)),
          UpgradeProvider(MetaProviderProto("galacticfog/gestalt-api-gateway:release-1.5.0"), MetaProviderProto("galacticfog/gestalt-api-gateway:release-1.6.0"), currentProviders(3))
        )))
      }
    }

  }

}
