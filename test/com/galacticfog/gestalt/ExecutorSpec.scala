package com.galacticfog.gestalt

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestActor, TestKit, TestProbe}
import com.galacticfog.gestalt.caas.{CaasClient, CaasClientFactory}
import com.google.inject.AbstractModule
import com.google.inject.name.Names
import mockws.MockWSHelpers
import net.codingwell.scalaguice.ScalaModule
import org.specs2.matcher.Matchers
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
    val metaExecutor = MetaProvider("fqon", "executor-name", uuid, ResourceIds.JavaExecutor, Some("image:actual"))

    val upgradeProvider = UpgradeProvider(expProto, tgtProto, metaProvider)
    val upgradeExecutor = UpgradeExecutor(expProto, tgtProto, metaExecutor)
    val upgradeBaseSvc = UpgradeBaseService("security", expProto.image, tgtProto.image, "image:actual")

    "properly upgrade Meta provider services" in new WithConfig {
      mockMetaClient.getProvider(metaProvider.fqon, metaProvider.id) returns Future.successful(metaProvider)
      mockMetaClient.updateProvider(any) answers {(a: Any) => Future.successful(a.asInstanceOf[MetaProvider])}
      await(executor.execute(upgradeProvider)) must matching(s"upgraded meta provider.*${metaProvider.id}.*from .*image:actual.* to .*image:target.*")
      there was one(mockMetaClient).getProvider(metaProvider.fqon, metaProvider.id)
      there was one(mockMetaClient).updateProvider(metaProvider.copy(
        image = Some(tgtProto.image)
      ))
    }

    "properly roll-back Meta provider services" in new WithConfig {
      mockMetaClient.updateProvider(any) answers {(a: Any) => Future.successful(a.asInstanceOf[MetaProvider])}
      await(executor.revert(upgradeProvider)) must matching(s"reverted meta provider.*${metaProvider.id}.*to image:actual")
      there was one(mockMetaClient).updateProvider(metaProvider)
    }

    "fail step if Meta provider is not as in plan" in new WithConfig {
      mockMetaClient.getProvider(metaProvider.fqon, metaProvider.id) returns Future.successful(metaProvider.copy(
        image = Some("image:different")
      ))
      await(executor.execute(upgradeProvider)) must throwA[RuntimeException]("different than in computed plan")
      there was one(mockMetaClient).getProvider(metaProvider.fqon, metaProvider.id)
      there were no(mockMetaClient).updateProvider(any)
    }


    "properly upgrade Meta executor provider" in new WithConfig {
      mockMetaClient.getProvider(metaExecutor.fqon, metaExecutor.id) returns Future.successful(metaExecutor)
      mockMetaClient.updateProvider(any) answers {(a: Any) => Future.successful(a.asInstanceOf[MetaProvider])}
      await(executor.execute(upgradeExecutor)) must matching(s"upgraded meta executor.*${metaExecutor.id}.*from .*image:actual.* to .*image:target.*")
      there was one(mockMetaClient).getProvider(metaExecutor.fqon, metaExecutor.id)
      there was one(mockMetaClient).updateProvider(metaExecutor.copy(
        image = Some(tgtProto.image)
      ))
    }

    "properly roll-back Meta executor provider" in new WithConfig {
      mockMetaClient.updateProvider(any) answers {(a: Any) => Future.successful(a.asInstanceOf[MetaProvider])}
      await(executor.revert(upgradeExecutor)) must matching(s"reverted meta executor.*${metaExecutor.id}.*to .*image:actual")
      there was one(mockMetaClient).updateProvider(metaExecutor)
    }

    "fail step if executor is not as in plan" in new WithConfig {
      mockMetaClient.getProvider(metaExecutor.fqon, metaExecutor.id) returns Future.successful(metaExecutor.copy(
        image = Some("image:different")
      ))
      await(executor.execute(upgradeExecutor)) must throwA[RuntimeException]("different than in computed plan")
      there was one(mockMetaClient).getProvider(metaExecutor.fqon, metaExecutor.id)
      there were no(mockMetaClient).updateProvider(any)
    }


    "properly upgrade base service" in new WithConfig {
      mockCaasClient.getCurrentImage("security") returns Future.successful("image:actual")
      mockCaasClient.updateImage(any, any, any) returns Future.successful(tgtProto.image)
      await(executor.execute(upgradeBaseSvc)) must matching(s"upgraded base service 'security' from .*image:actual.* to .*image:target")
      there was one(mockCaasClient).updateImage("security", tgtProto.image, Seq("image:actual", "image:target"))
    }

    "properly roll-back base service" in new WithConfig {
      mockCaasClient.updateImage(any, any, any) returns Future.successful("image:actual")
      await(executor.revert(upgradeBaseSvc)) must matching(s"reverted base service 'security' to image:actual")
      there was one(mockCaasClient).updateImage("security", "image:actual", Seq.empty)
    }

  }

}
