package com.galacticfog.gestalt

import akka.actor.{ActorRef, ActorSystem}
import akka.persistence.fsm.PersistentFSM.{CurrentState, SubscribeTransitionCallBack, Transition}
import akka.testkit.{ImplicitSender, TestKit}
import com.google.inject.AbstractModule
import net.codingwell.scalaguice.ScalaModule
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.inject.BindingKey
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.concurrent.AkkaGuiceSupport

import scala.concurrent.Future
import scala.concurrent.duration._

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

    "handle an empty upgrade plan" in new WithConfig() {
      val upgraderRef: ActorRef = injector.instanceOf(BindingKey(classOf[ActorRef]).qualifiedWith(Upgrader.actorName))

      upgraderRef ! SubscribeTransitionCallBack(testActor)
      expectMsg(CurrentState(upgraderRef, Upgrader.Stopped, None))

      upgraderRef ! Upgrader.GetLog
      expectMsg(Seq.empty)

      upgraderRef ! Upgrader.StartUpgrade(Seq.empty)
      expectMsg(Transition(upgraderRef, Upgrader.Stopped, Upgrader.Complete, None))

      upgraderRef ! Upgrader.GetLog
      expectMsg(Seq(
        "Upgrade started",
        "Upgrade complete"
      ))

      there was no(mockExecutor).execute(any)
    }

    "execute all upgrade steps in plan" in new WithConfig() {
      val upgraderRef: ActorRef = injector.instanceOf(BindingKey(classOf[ActorRef]).qualifiedWith(Upgrader.actorName))

      mockExecutor.execute(plan(0)) returns Future.successful("step0")
      mockExecutor.execute(plan(1)) returns Future.successful("step1")
      mockExecutor.execute(plan(2)) returns Future.successful("step2")
      mockExecutor.execute(plan(3)) returns Future.successful("step3")

      upgraderRef ! SubscribeTransitionCallBack(testActor)
      expectMsg(CurrentState(upgraderRef, Upgrader.Stopped, None))

      upgraderRef ! Upgrader.GetLog
      expectMsg(Seq.empty)

      upgraderRef ! Upgrader.StartUpgrade(plan)
      expectMsg(Transition(upgraderRef, Upgrader.Stopped, Upgrader.Upgrading, None))
      expectMsg(Transition(upgraderRef, Upgrader.Upgrading, Upgrader.Complete, None))

      upgraderRef ! Upgrader.GetLog
      expectMsg(Seq(
        "Upgrade started",
        "step0",
        "step1",
        "step2",
        "step3",
        "Upgrade complete"
      ))

      there was one(mockExecutor).execute(plan(0))
      there was one(mockExecutor).execute(plan(1))
      there was one(mockExecutor).execute(plan(2))
      there was one(mockExecutor).execute(plan(3))
    }

    "fail if an upgrade step fails" in new WithConfig() {
      val upgraderRef: ActorRef = injector.instanceOf(BindingKey(classOf[ActorRef]).qualifiedWith(Upgrader.actorName))

      mockExecutor.execute(plan(0)) returns Future.successful("step0")
      mockExecutor.execute(plan(1)) returns Future.successful("step1")
      mockExecutor.execute(plan(2)) returns Future.failed(new RuntimeException("step2 failed"))

      upgraderRef ! SubscribeTransitionCallBack(testActor)
      expectMsg(CurrentState(upgraderRef, Upgrader.Stopped, None))

      upgraderRef ! Upgrader.GetLog
      expectMsg(Seq.empty)

      upgraderRef ! Upgrader.StartUpgrade(plan)
      expectMsg(Transition(upgraderRef, Upgrader.Stopped, Upgrader.Upgrading, None))
      expectMsg(Transition(upgraderRef, Upgrader.Upgrading, Upgrader.Failed, None))

      upgraderRef ! Upgrader.GetLog
      expectMsg(Seq(
        "Upgrade started",
        "step0",
        "step1",
        "step2 failed"
      ))

      there was one(mockExecutor).execute(plan(0))
      there was one(mockExecutor).execute(plan(1))
      there was one(mockExecutor).execute(plan(2))
      there was  no(mockExecutor).execute(plan(3))
    }

  }

}
