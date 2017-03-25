package com.actionml.router.http.routes

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import cats.data.Validated
import com.actionml.core.validate.ValidateError
import com.actionml.router.service._
import io.circe.Json
import scaldi.Injector

import scala.language.postfixOps

/**
  *
  * Event endpoints:
  *
  * Add new event
  * PUT, POST /datasets/<dataset-id>/events {JSON body for PIO event}
  * Response: HTTP code 201 if the event was successfully created; otherwise, 400.
  *
  * Get exist event
  * GET /datasets/<dataset-id>/events/<event-id>
  * Response: {JSON body for PIO event}
  * HTTP code 200 if the event exist; otherwise, 404
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 28.01.17 12:53
  */
class EventsRouter(implicit inj: Injector) extends BaseRouter {

  private val eventService = injectActorRef[EventService]

  val route: Route = rejectEmptyResponse {
    (pathPrefix("datasets" / Segment) & extractLog) { (datasetId, log) =>
      pathPrefix("events") {
        pathEndOrSingleSlash {
          createEvent(datasetId, log)
        } ~ path(Segment) { eventId ⇒
          getEvent(datasetId, eventId, log)
        }
      }
    }
  }

  private def getEvent(datasetId: String, eventId: String, log: LoggingAdapter): Route = get {
    log.debug("Get event: {}, {}", datasetId, eventId)
    complete()
//    complete((eventService ? GetEvent(datasetId, eventId))
//      .mapTo[Option[CBRawEvent]]
//      .map(_.map(_.asJson))
//    )
  }

  private def createEvent(datasetId: String, log: LoggingAdapter): Route = ((post | put) & entity(as[Json])) { event =>
    log.debug("Create event: {}, {}", datasetId, event)
    completeByValidated(StatusCodes.Created) {
      (eventService ? CreateEvent(datasetId, event.toString())).mapTo[Validated[ValidateError, Json]]
    }
  }

}
