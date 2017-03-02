import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, MediaTypes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.actionml.entity.{Event, EventId}
import com.actionml.router.http.RestServer
import com.actionml.router.http.routes.EventsRouter
import com.actionml.router.service._
import de.heikoseeberger.akkahttpcirce.CirceSupport
import io.circe.generic.auto._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}
import scaldi.akka.AkkaInjectable
import scaldi.{Injector, Module}

class EventsRouterTest extends FlatSpec with Matchers with ScalatestRouteTest with ScalaFutures with CirceSupport with AkkaInjectable{

  private val existDatasetId = "EXIST_DATASET_ID"
  private val notExistDatasetId = "UNKNOWN_DATASET_ID"
  private val existEventId = "EXIST_EVENT_ID"
  private val notExistEventId = "UNKNOWN_EVENT_ID"
  private val genEventID = "GEN_EVENT_ID"

  private val existEvent = Event(
    event = "EVENT_NAME",
    entityType = "ENTITY_TYPE",
    entityId = "ENTITY_ID"
  )

  implicit val injector = new BaseModule

  private val routes = inject[EventsRouter].route

  private val validJson =
    """
      |{
      |  "event" : "my_event",
      |  "entityType" : "user",
      |  "entityId" : "uid",
      |  "properties" : {
      |    "prop1" : 1,
      |    "prop2" : "value2",
      |    "prop3" : [1, 2, 3],
      |    "prop4" : true,
      |    "prop5" : ["a", "b", "c"],
      |    "prop6" : 4.56
      |  },
      |  "eventTime" : "2004-12-13T21:39:45.618-07:00"
      |}
    """.stripMargin

  private val invalidJson =
    """
      |{
      |  "event" : "my_event",
      |  "entityType" : "user"
      |}
    """.stripMargin

  private val validJsonEntity = HttpEntity(MediaTypes.`application/json`, validJson)
  private val invalidJsonEntity = HttpEntity(MediaTypes.`application/json`, invalidJson)
  private val emptyEntity = HttpEntity("")

  "EventsRouter" should "create new event" in {

    // Send valid json, expect Created 201
    Post(s"/datasets/$existDatasetId/events", validJsonEntity) ~> routes ~> check {
      status shouldBe Created
      contentType shouldBe ContentTypes.`application/json`
      responseAs[EventId] should be(EventId(genEventID))
    }

    // Send invalid json, expect BadRequest 400
    Post(s"/datasets/$existDatasetId/events", invalidJsonEntity) ~> Route.seal(routes) ~> check {
      status shouldBe BadRequest
    }

    // Send empty, expect UnsupportedMediaType 415
    Post(s"/datasets/$existDatasetId/events", emptyEntity) ~> Route.seal(routes) ~> check {
      status shouldBe UnsupportedMediaType
    }

    // Send GET, expect MethodNotAllowed 405
    Get(s"/datasets/$existDatasetId/events", validJsonEntity) ~> Route.seal(routes) ~> check {
      status shouldBe MethodNotAllowed
    }

    // Send to unknown dataset, expect NotFound 404
    Post(s"/datasets/$notExistDatasetId/events", validJsonEntity) ~> Route.seal(routes) ~> check {
      status shouldBe NotFound
    }
  }

  "EventsRouter" should "get event" in {
    Get(s"/datasets/$existDatasetId/events/$existEventId") ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe ContentTypes.`application/json`
      responseAs[Event] should be(existEvent)
    }

    Get(s"/datasets/$notExistDatasetId/events/$existEventId", validJsonEntity) ~> Route.seal(routes) ~> check {
      status shouldBe NotFound
    }
  }

  class BaseModule extends Module{
    bind[ActorSystem] to ActorSystem("test") destroyWith(_.terminate())
    bind[RestServer] to new RestServer
    bind[EventsRouter] to new EventsRouter
    bind[EventService] to new MockEventService
  }

  class MockEventService(implicit inj: Injector) extends EventService with AkkaInjectable{
    override def receive: Receive = {

      case GetEvent(datasetId, eventId) ⇒
        sender() ! send(datasetId, existEvent)

      case CreateEvent(datasetId, event) ⇒
        sender() ! send(datasetId, EventId(genEventID))

    }

    private def send(datasetId: String, msg: Any): Option[Any] = {
      if (datasetId == existDatasetId) {
        Some(msg)
      } else {
        None
      }
    }

  }

}
