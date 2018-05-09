package com.galacticfog.gestalt

import java.util.UUID

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.google.inject.AbstractModule
import mockws.{MockWS, MockWSHelpers, Route}
import net.codingwell.scalaguice.ScalaModule
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AsyncHttpClientProvider
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClient
import play.api.mvc.Results._
import play.api.test._
import play.api.http.HttpVerbs._
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class MetaClientSpec extends Specification with Mockito with MockWSHelpers with FutureAwaits with DefaultAwaitTimeout {

  val metaHost = "meta.test"
  val metaPort = 14374

  "DefaultMetaClient" should {

    def uuid = java.util.UUID.randomUUID()

    case class TestModule(ws: WSClient) extends AbstractModule with ScalaModule with AkkaGuiceSupport {
      override def configure(): Unit = {
        bind[WSClient].toInstance(ws)
        bind[MetaClient].to[DefaultMetaClient]
        bind[AsyncHttpClient].toProvider[AsyncHttpClientProvider]
      }
    }

    abstract class WithRoutesAndConfig(routes: MockWS.Routes, config: (String,Any)*)
      extends TestKit(ActorSystem("test-system")) with Scope with ImplicitSender {

      val mockWSClient = MockWS(routes)
      val injector =
        new GuiceApplicationBuilder()
          .disable[modules.DefaultComponentModule]
          .disable[play.api.libs.ws.ahc.AhcWSModule]
          .bindings(TestModule(ws = mockWSClient))
          .configure(config:_*)
          .injector

      val metaClient = injector.instanceOf[MetaClient]
      implicit val ec = system.dispatcher
    }

    val allProviders = Route({
      case (GET, url) if url == s"http://$metaHost:$metaPort/orgs" => Action{ request =>
        if (request.getQueryString("expand").contains("true")) {
          val resp = Json.parse(scala.io.Source.fromInputStream(getClass.getResourceAsStream("/fqon-list.json")).getLines().mkString)
          Ok(resp)
        } else Ok(Json.arr())
      }
      case (GET, url) if url == s"http://$metaHost:$metaPort/root/providers" => Action{ request =>
        if (request.getQueryString("expand").contains("true")) {
          val resp = Json.parse(scala.io.Source.fromInputStream(getClass.getResourceAsStream("/root-provider-list.json")).getLines().mkString)
          Ok(resp)
        } else Ok(Json.arr())
      }
      case (GET, url) if url == s"http://$metaHost:$metaPort/engineering/providers" => Action{ request =>
        if (request.getQueryString("expand").contains("true")) {
          val resp = Json.parse(scala.io.Source.fromInputStream(getClass.getResourceAsStream("/eng-provider-list.json")).getLines().mkString)
          Ok(resp)
        } else Ok(Json.arr())
      }
    })

    val notFoundRoute = Route({
      case (_,_) => Action(NotFound("this is the notFoundRoute in the test; something is probably not correct ;)"))
    })

    "parse meta providers" in {
      val mc = new MetaClientParsing {}
      val resp = Json.parse(scala.io.Source.fromInputStream(getClass.getResourceAsStream("/eng-provider-list.json")).getLines().mkString).as[Seq[JsObject]]
      resp.flatMap(mc.parseMetaProvider) must haveSize(2)
    }

    "get all providers" in new WithRoutesAndConfig(
      routes = allProviders orElse notFoundRoute,
      "meta.host" -> metaHost,
      "meta.port" -> metaPort,
      "security.key" -> "open",
      "security.secret" -> "sesame"
    ) {
      await(metaClient.listProviders) must containTheSameElementsAs(Seq(
        MetaProvider("root","golang-executor-executor",UUID.fromString("cc4bcf76-7673-46d1-805a-44e5313fce3a"),ResourceIds.GoLangExecutor,None,Json.parse("""{"env":{"private":{},"public":{"CMD":"bin/gestalt-laser-executor-golang","IMAGE":"galacticfog/gestalt-laser-executor-golang:1.15.0-SNAPSHOT-0fcd7851","NAME":"golang-executor","RUNTIME":"golang"}}}""").as[JsObject]),
        MetaProvider("root","default-security",UUID.fromString("4f1dc4df-b26e-4001-a4d2-4b8bf4d8a4c8"),ResourceIds.SecurityProvider,None,Json.parse("""{"env":{"private":{},"public":{"HOSTNAME":"test-galacticfog-com-security.gestalt","KEY":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","PORT":"9455","PROTOCOL":"http","SECRET":"sssshhhhhhhh"}}}""").as[JsObject]),
        MetaProvider("engineering","default-rabbit",UUID.fromString("38eec176-d43b-423c-9a28-e4b13cb838c4"),ResourceIds.RabbitProvider,Some("rabbit:latest"),Json.parse("""{"env":{"private":{},"public":{"SERVICE_HOST":"rabbit.test-galacticfog-com.marathon.mesos","SERVICE_PORT":"5672"}}}""").as[JsObject]),
        MetaProvider("engineering","jvm-executor-executor",UUID.fromString("02480d90-bd62-4a37-a1c2-5be0afeab5ff"),ResourceIds.JavaExecutor,None,Json.parse("""{"env":{"private":{},"public":{"CMD":"bin/gestalt-laser-executor-jvm","IMAGE":"galacticfog/gestalt-laser-executor-jvm:1.21.6-SNAPSHOT-a797b743","NAME":"jvm-executor","RUNTIME":"java;scala"}}}""").as[JsObject])
      ))
    }

  }

}
