import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, MediaTypes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.actionml.entity.Event
import com.actionml.http.RestServer
import com.actionml.http.routes.EventsRouter
import com.actionml.service._
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

  private val json =
    """
      |{
      |  "event" : "my_event",
      |  "entityType" : "user",
      |  "entityId" : "uid",
      |  "eventTime" : "2004-12-13T21:39:45.618-07:00"
      |}
    """.stripMargin
  private val httpEntity = HttpEntity(MediaTypes.`application/json`, json)

  "EventsRouter" should "create new event" in {
    Put(s"/datasets/$existDatasetId/events", httpEntity) ~> routes ~> check {
      status shouldBe Created
      contentType shouldBe ContentTypes.`application/json`
      responseAs[EventId] should be(EventId(genEventID))
    }

    Put(s"/datasets/$notExistDatasetId/events", httpEntity) ~> Route.seal(routes) ~> check {
      status shouldBe NotFound
    }
  }

  "EventsRouter" should "get event" in {
    Get(s"/datasets/$existDatasetId/events/$existEventId") ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe ContentTypes.`application/json`
      responseAs[Event] should be(existEvent)
    }

    Get(s"/datasets/$notExistDatasetId/events/$existEventId", httpEntity) ~> Route.seal(routes) ~> check {
      status shouldBe NotFound
    }
  }

  class BaseModule extends Module{
    bind[ActorSystem] to ActorSystem("ActionMLRestServer") destroyWith(_.terminate())
    bind[RestServer] to new RestServer
    bind[EventsRouter] to new EventsRouter
    bind[EventService] to new MockEventService
  }

  class MockEventService(implicit inj: Injector) extends EventService with AkkaInjectable{
    override def receive: Receive = {

      case GetEvent(datasetId, eventId) ⇒
        log.info("Create new entity, {}, {}", datasetId, eventId)
        sender() ! send(datasetId, existEvent)

      case CreateEvent(datasetId, event) ⇒
        log.info("Create new entity, {}, {}", datasetId, event)
        sender() ! send(datasetId, EventId(genEventID))

    }

    private def send(datasetId: String, msg: Any): Option[Any] = {
      if (datasetId == existDatasetId) {
        log.info("Dataset OK")
        Some(msg)
      } else {
        log.info("Dataset ERROR")
        None
      }
    }

  }

}
