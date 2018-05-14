package com.galacticfog.gestalt

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestActor, TestKit, TestProbe}
import com.galacticfog.gestalt.caas.{CaasClient, CaasClientFactory}
import com.google.inject.AbstractModule
import com.google.inject.name.Names
import mockws.MockWSHelpers
import net.codingwell.scalaguice.ScalaModule
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.test._

import scala.concurrent.{ExecutionContext, Future}

class ExecutorSpec extends Specification with Mockito with MockWSHelpers with FutureAwaits with DefaultAwaitTimeout {

  val actorSystem = ActorSystem("test")

  "DefaultExecutor" should {

    def uuid = java.util.UUID.randomUUID()

    case class TestModule(caasClient: CaasClient) extends AbstractModule with ScalaModule with AkkaGuiceSupport {
      val cf = TestProbe()(actorSystem)
      cf.setAutoPilot(new TestActor.AutoPilot {
        def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
          msg match {
            case CaasClientFactory.GetClient =>
              sender ! caasClient
              TestActor.KeepRunning
          }
        }
      })

      override def configure(): Unit = {
        bind[Executor].to[DefaultExecutor]
        bind[MetaClient].toInstance(mock[MetaClient])
        bind(classOf[ActorRef]).annotatedWith(Names.named(CaasClientFactory.actorName)).toInstance(cf.ref)
      }
    }

    abstract class WithConfig(config: (String,Any)*)
      extends TestKit(ActorSystem("test-system")) with Scope with ImplicitSender {

      val mockCaasClient = mock[CaasClient]

      val injector =
        new GuiceApplicationBuilder()
          .disable[modules.DefaultComponentModule]
          .bindings(TestModule(mockCaasClient))
          .configure(config:_*)
          .injector

      val mockMetaClient = injector.instanceOf[MetaClient]
      val executor = injector.instanceOf[Executor]
      implicit val ec = injector.instanceOf[ExecutionContext]
    }

    val expProto = MetaProviderProto("image:expected")
    val tgtProto = MetaProviderProto("image:target")
    val metaProvider = MetaProvider("fqon", "provider-name", uuid, ResourceIds.Provider, Some("image:actual"))

    "properly upgrade Meta provider services" in new WithConfig {
      mockMetaClient.getProvider(metaProvider.fqon, metaProvider.id) returns Future.successful(metaProvider)
      mockMetaClient.updateProvider(any) answers {(a: Any) => Future.successful(a.asInstanceOf[MetaProvider])}
      await(executor.execute(UpgradeProvider(expProto, tgtProto, metaProvider))) must matching(s"upgraded meta provider.*${metaProvider.id}.*from image:actual to image:target")
      there was one(mockMetaClient).getProvider(metaProvider.fqon, metaProvider.id)
      there was one(mockMetaClient).updateProvider(metaProvider.copy(
        image = Some(tgtProto.image)
      ))
    }

    "properly roll-back Meta provider services" in new WithConfig {
      mockMetaClient.updateProvider(any) answers {(a: Any) => Future.successful(a.asInstanceOf[MetaProvider])}
      await(executor.revert(UpgradeProvider(expProto, tgtProto, metaProvider))) must matching(s"reverted meta provider.*${metaProvider.id}.*to image:actual")
      there was one(mockMetaClient).updateProvider(metaProvider)
    }

    "fail step if actual is not actual" in new WithConfig {
      mockMetaClient.getProvider(metaProvider.fqon, metaProvider.id) returns Future.successful(metaProvider.copy(
        image = Some("image:different")
      ))
      await(executor.execute(UpgradeProvider(expProto, tgtProto, metaProvider))) must throwA[RuntimeException]("different than in computed plan")
      there was one(mockMetaClient).getProvider(metaProvider.fqon, metaProvider.id)
      there were no(mockMetaClient).updateProvider(any)
    }


  }

}
