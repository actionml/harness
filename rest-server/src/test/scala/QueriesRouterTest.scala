import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, MediaTypes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.actionml.entity.PredictionResult
import com.actionml.router.http.RestServer
import com.actionml.router.http.routes.QueriesRouter
import com.actionml.router.service._
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.CirceSupport
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}
import scaldi.akka.AkkaInjectable
import scaldi.{Injector, Module}

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 25.02.17 12:06
  */
class QueriesRouterTest extends FlatSpec with Matchers with ScalatestRouteTest with ScalaFutures with CirceSupport with AkkaInjectable{

  private val existEngineId = "EXIST_ENGINE_ID"
  private val notExistEngineId = "UNKNOWN_ENGINE_ID"

  implicit val injector = new BaseModule

  private val routes = inject[QueriesRouter].route

  private val query =
    """
      |{
      |  "user" : "USER-1",
      |  "num" : 10
      |}
    """.stripMargin

  private val prediction = PredictionResult()

  private val queryEntity = HttpEntity(MediaTypes.`application/json`, query)
  private val emptyEntity = HttpEntity("")

  "QueriesRouter" should "get prediction" in {

    // Send valid json, expect OK 200
    Post(s"/engines/$existEngineId/queries", queryEntity) ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe ContentTypes.`application/json`
      responseAs[PredictionResult] should be(prediction)
    }

    // Send empty, expect UnsupportedMediaType 415
    Post(s"/engines/$existEngineId/queries", emptyEntity) ~> Route.seal(routes) ~> check {
      status shouldBe UnsupportedMediaType
    }

    // Send GET, expect MethodNotAllowed 405
    Get(s"/engines/$existEngineId/queries", queryEntity) ~> Route.seal(routes) ~> check {
      status shouldBe MethodNotAllowed
    }

    // Send to unknown dataset, expect NotFound 404
    Post(s"/engines/$notExistEngineId/queries", queryEntity) ~> Route.seal(routes) ~> check {
      status shouldBe NotFound
    }
  }

  class BaseModule extends Module{
    bind[ActorSystem] to ActorSystem("test") destroyWith(_.terminate())
    bind[RestServer] to new RestServer
    bind[QueriesRouter] to new QueriesRouter
    bind[QueryService] to new MockQueryService
  }

  class MockQueryService(implicit inj: Injector) extends QueryService{
    override def receive: Receive = {
      case GetPrediction(engineId, query) ⇒
        sender() ! send(engineId, prediction)
    }

    private def send(engineId: String, msg: Any): Option[Any] = {
      if (engineId == existEngineId) {
        Some(msg)
      } else {
        None
      }
    }

  }

}
