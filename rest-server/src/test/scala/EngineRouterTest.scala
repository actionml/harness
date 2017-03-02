import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, MediaTypes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.actionml.entity.{Engine, EngineId}
import com.actionml.router.http.RestServer
import com.actionml.router.http.routes.EnginesRouter
import com.actionml.router.service._
import de.heikoseeberger.akkahttpcirce.CirceSupport
import io.circe.generic.auto._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}
import scaldi.akka.AkkaInjectable
import scaldi.{Injector, Module}

class EngineRouterTest extends FlatSpec with Matchers with ScalatestRouteTest with ScalaFutures with CirceSupport with AkkaInjectable {

  private val existEngineId = "EXIST_ENGINE_ID"
  private val notExistEngineId = "UNKNOWN_ENGINE_ID"
  private val newEngineId = "NEW_ENGINE_ID"

  implicit val injector = new BaseModule

  private val routes = inject[EnginesRouter].route

  private val validJson =
    """
      |{
      |  "id" : "engine_id"
      |}
    """.stripMargin
  private val validEngine = HttpEntity(MediaTypes.`application/json`, validJson)

  private val invalidJson =
    """
      |{
      |  "unknownField": "jkb4ryef9yt8sdvy"
      |}
    """.stripMargin
  private val invalidEngine = HttpEntity(MediaTypes.`application/json`, invalidJson)

  "EnginesRouter" should "create new engine" in {
    Put("/engines/", validEngine) ~> routes ~> check {
      status shouldBe Created
      contentType shouldBe ContentTypes.`application/json`
      responseAs[EngineId] should be(EngineId(newEngineId))
    }

    Put("/engines/", invalidEngine) ~> Route.seal(routes) ~> check {
      status shouldBe BadRequest
    }

    Put("/engines/") ~> Route.seal(routes) ~> check {
      status shouldBe BadRequest
    }
  }

  "EnginesRouter" should "update exist engine" in {
    Put(s"/engines/$existEngineId", validEngine) ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe ContentTypes.`application/json`
      responseAs[Boolean] should be(true)
    }

    Put(s"/engines/$existEngineId", invalidEngine) ~> Route.seal(routes) ~> check {
      status shouldBe BadRequest
    }

    Put(s"/engines/$notExistEngineId", validEngine) ~> Route.seal(routes) ~> check {
      status shouldBe NotFound
    }
  }

  "EnginesRouter" should "get exist engine" in {
    Get(s"/engines/$existEngineId") ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe ContentTypes.`application/json`
      responseAs[Engine] should be(Engine(existEngineId))
    }

    Get(s"/engines/$notExistEngineId") ~> Route.seal(routes) ~> check {
      status shouldBe NotFound
    }
  }

  "EnginesRouter" should "delete exist engine" in {
    Delete(s"/engines/$existEngineId") ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe ContentTypes.`application/json`
      responseAs[Boolean] should be(true)
    }

    Delete(s"/engines/$notExistEngineId") ~> Route.seal(routes) ~> check {
      status shouldBe NotFound
    }
  }

  class BaseModule extends Module {
    bind[ActorSystem] to ActorSystem("test") destroyWith (_.terminate())
    bind[RestServer] to new RestServer
    bind[EnginesRouter] to new EnginesRouter
    bind[EngineService] to new MockEngineService
  }

  class MockEngineService(implicit inj: Injector) extends EngineService with AkkaInjectable {
    override def receive: Receive = {

      case GetEngine(engineId) ⇒
        log.info("Get engine, {}", engineId)
        if (engineId == existEngineId) {
          sender() ! Some(Engine(existEngineId))
        } else {
          sender() ! None
        }

      case CreateEngine(engine) ⇒
        log.info("Create new engine, {}", engine)
        sender() ! Some(EngineId(newEngineId))

      case UpdateEngine(engineId, engine) ⇒
        log.info("Update exist engine, {}, {}", engineId, engine)
        if (engineId == existEngineId) {
          sender() ! Some(true)
        } else {
          sender() ! None
        }

      case DeleteEngine(engineId) ⇒
        log.info("Delete exist engine, {}", engineId)
        if (engineId == existEngineId) {
          sender() ! Some(true)
        } else {
          sender() ! None
        }

    }

  }

}
