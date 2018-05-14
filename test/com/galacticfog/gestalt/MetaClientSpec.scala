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
import play.api.libs.json.{JsObject, JsValue, Json}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class MetaClientSpec extends Specification with Mockito with MockWSHelpers with FutureAwaits with DefaultAwaitTimeout {

  val metaCallbackUrl = "http://meta.test:14374"

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

    var updatePayload: JsValue = null
    def updateAndRedeployServiceProvider(metaProvider: MetaProvider) = Route({
      case (PATCH, url) if url == s"$metaCallbackUrl/${metaProvider.fqon}/providers/${metaProvider.id}" => Action(BodyParser.json) { request =>
        updatePayload = request.body
        Ok(Json.obj(
          "id" -> metaProvider.id,
          "name" -> metaProvider.name,
          "resource_type" -> resourceName(metaProvider.providerType),
          "org" -> Json.obj(
            "properties" -> Json.obj(
              "fqon" -> metaProvider.fqon
            )
          ),
          "properties" -> Json.obj(
            "services" -> Seq(Json.obj(
              "container_spec" -> Json.obj(
                "properties" -> Json.obj(
                  "image" -> metaProvider.image
                )
              )
            ))
          )
        ))
      }
      case (POST, url) if url == s"$metaCallbackUrl/${metaProvider.fqon}/providers/${metaProvider.id}/redeploy" => Action { request =>
        Accepted
      }
    })

    def updateExecutorProvider(metaProvider: MetaProvider) = Route({
      case (PATCH, url) if url == s"$metaCallbackUrl/${metaProvider.fqon}/providers/${metaProvider.id}" => Action(BodyParser.json) { request =>
        updatePayload = request.body
        Ok(Json.obj(
          "id" -> metaProvider.id,
          "name" -> metaProvider.name,
          "resource_type" -> resourceName(metaProvider.providerType),
          "org" -> Json.obj(
            "properties" -> Json.obj(
              "fqon" -> metaProvider.fqon
            )
          ),
          "properties" -> Json.obj(
            "config" -> Json.obj(
              "env" -> Json.obj(
                "public" -> Json.obj(
                  "IMAGE" -> metaProvider.image
                )
              )
            )
          )
        ))
      }
    })

    val testProviderId = uuid
    val singleProvider = Route({
      case (GET, url) if url == s"$metaCallbackUrl/root/providers/$testProviderId" => Action {
        val resp = Json.parse(scala.io.Source.fromInputStream(getClass.getResourceAsStream("/single-meta-provider.json")).getLines().mkString)
        Ok(resp)
      }
    })

    val allProviders = Route({
      case (GET, url) if url == s"$metaCallbackUrl/orgs" => Action{ request =>
        if (request.getQueryString("expand").contains("true")) {
          val resp = Json.parse(scala.io.Source.fromInputStream(getClass.getResourceAsStream("/fqon-list.json")).getLines().mkString)
          Ok(resp)
        } else Ok(Json.arr())
      }
      case (GET, url) if url == s"$metaCallbackUrl/root/providers" => Action{ request =>
        if (request.getQueryString("expand").contains("true")) {
          val resp = Json.parse(scala.io.Source.fromInputStream(getClass.getResourceAsStream("/root-provider-list.json")).getLines().mkString)
          Ok(resp)
        } else Ok(Json.arr())
      }
      case (GET, url) if url == s"$metaCallbackUrl/engineering/providers" => Action{ request =>
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
      resp.flatMap(mc.parseMetaProvider) must haveSize(4)
    }

    "get a single provider" in new WithRoutesAndConfig(
      routes = singleProvider orElse notFoundRoute,
      "meta.callback-url" -> "http://meta.test:14374/",
      "security.key" -> "open",
      "security.secret" -> "sesame"
    ) {
      await(metaClient.getProvider("root", testProviderId)) must_==
        MetaProvider("root","golang-executor-executor",UUID.fromString("cc4bcf76-7673-46d1-805a-44e5313fce3a"),ResourceIds.GoLangExecutor,None,Json.parse("""{"env":{"private":{},"public":{"CMD":"bin/gestalt-laser-executor-golang","NAME":"golang-executor","RUNTIME":"golang"}}}""").as[JsObject]),
    }

    "not-get a single provider with an appropriate error message" in new WithRoutesAndConfig(
      routes = notFoundRoute,
      "meta.callback-url" -> "http://meta.test:14374/",
      "security.key" -> "open",
      "security.secret" -> "sesame"
    ) {
      await(metaClient.getProvider("root", testProviderId)) must throwAn[RuntimeException]("provider.*not found")
    }

    "get all providers" in new WithRoutesAndConfig(
      routes = allProviders orElse notFoundRoute,
      "meta.callback-url" -> "http://meta.test:14374/",
      "security.key" -> "open",
      "security.secret" -> "sesame"
    ) {
      await(metaClient.listProviders) must containTheSameElementsAs(Seq(
        MetaProvider("root","golang-executor-executor",UUID.fromString("cc4bcf76-7673-46d1-805a-44e5313fce3a"),ResourceIds.GoLangExecutor,None,Json.parse("""{"env":{"private":{},"public":{"CMD":"bin/gestalt-laser-executor-golang","NAME":"golang-executor","RUNTIME":"golang"}}}""").as[JsObject]),
        MetaProvider("root","default-security",UUID.fromString("4f1dc4df-b26e-4001-a4d2-4b8bf4d8a4c8"),ResourceIds.SecurityProvider,None,Json.parse("""{"env":{"private":{},"public":{"HOSTNAME":"test-galacticfog-com-security.gestalt","KEY":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","PORT":"9455","PROTOCOL":"http","SECRET":"sssshhhhhhhh"}}}""").as[JsObject]),
        MetaProvider("engineering","default-rabbit",UUID.fromString("38eec176-d43b-423c-9a28-e4b13cb838c4"),ResourceIds.RabbitProvider,Some("rabbit:latest"),Json.parse("""{"env":{"private":{},"public":{"SERVICE_HOST":"rabbit.test-galacticfog-com.marathon.mesos","SERVICE_PORT":"5672"}}}""").as[JsObject]),
        MetaProvider("engineering","jvm-executor-executor",UUID.fromString("02480d90-bd62-4a37-a1c2-5be0afeab5ff"),ResourceIds.JavaExecutor,Some("galacticfog/gestalt-laser-executor-jvm:1.21.6-SNAPSHOT-a797b743"),Json.parse("""{"env":{"private":{},"public":{"CMD":"bin/gestalt-laser-executor-jvm","IMAGE":"galacticfog/gestalt-laser-executor-jvm:1.21.6-SNAPSHOT-a797b743","NAME":"jvm-executor","RUNTIME":"java;scala"}}}""").as[JsObject])
      ))
    }

    val svcProvider = MetaProvider(
      fqon = "some-org",
      name = "some-svc-provider",
      id = uuid,
      providerType = ResourceIds.KongGateway,
      image = Some("image:new-svc-image")
    )

    val execProvider = MetaProvider(
      fqon = "some-org",
      name = "some-executor",
      id = uuid,
      providerType = ResourceIds.NashornExecutor,
      image = Some("image:new-image")
    )

    "update and redeploy service provider" in new WithRoutesAndConfig(
      routes = updateAndRedeployServiceProvider(svcProvider) orElse notFoundRoute,
      "meta.callback-url" -> "http://meta.test:14374/",
      "security.key" -> "open",
      "security.secret" -> "sesame"
    ) {
      await(metaClient.updateProvider(svcProvider)).image must beSome(svcProvider.image.get)
      updatePayload must_== Json.arr(Json.obj(
        "path" -> "/properties/services/0/container_spec/properties/image",
        "op" -> "replace",
        "value" -> svcProvider.image
      ))
    }

    "update and redeploy executor provider" in new WithRoutesAndConfig(
      routes = updateExecutorProvider(execProvider) orElse notFoundRoute,
      "meta.callback-url" -> "http://meta.test:14374/",
      "security.key" -> "open",
      "security.secret" -> "sesame"
    ) {
      await(metaClient.updateProvider(execProvider)).image must beSome(execProvider.image.get)
      updatePayload must_== Json.arr(Json.obj(
        "path" -> "/properties/config/env/public/IMAGE",
        "op" -> "replace",
        "value" -> execProvider.image
      ))
    }

  }

}
