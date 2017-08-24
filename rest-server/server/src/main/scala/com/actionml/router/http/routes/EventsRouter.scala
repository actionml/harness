package com.actionml.router.http.routes

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.actionml.authserver.ResourceId
import com.actionml.authserver.Roles.event
import com.actionml.authserver.directives.AuthDirectives
import com.actionml.authserver.services.AuthServerClientService
import com.actionml.router.config.AppConfig
import com.actionml.router.service._
import io.circe.Json
import scaldi.Injector

import scala.language.postfixOps

/**
  *
  * Event endpoints:
  *
  * Add new event
  * PUT, POST /engines/<engine-id>/events {JSON body for PIO event}
  * Response: HTTP code 201 if the event was successfully created; otherwise, 400.
  *
  * Get exist event
  * GET /engines/<engine-id>/events/<event-id>
  * Response: {JSON body for PIO event}
  * HTTP code 200 if the event exist; otherwise, 404
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 28.01.17 12:53
  */
class EventsRouter(implicit inj: Injector) extends BaseRouter with AuthDirectives {
  private val eventService = inject[ActorRef]('EventService)
  override val authServerClientService = inject[AuthServerClientService]
  override val config = inject[AppConfig]

  val route: Route = (rejectEmptyResponse & extractAccessToken) { implicit accessToken =>
    pathPrefix("engines" / Segment) { engineId =>
      (pathPrefix("events") & extractLog) { implicit log =>
        (pathEndOrSingleSlash & hasAccess(event.create, ResourceId.*)) {
          createEvent(engineId, log)
        } ~
        path(Segment) { eventId ⇒
          hasAccess(event.read, eventId).apply {
            getEvent(engineId, eventId, log)
          }
        }
      }
    }
  }

  private def getEvent(datasetId: String, eventId: String, log: LoggingAdapter): Route = get {
    log.debug("Get event: {}, {}", datasetId, eventId)
    completeByValidated(StatusCodes.OK) {
      (eventService ? GetEvent(datasetId, eventId)).mapTo[Response]
    }
  }

  private def createEvent(engineId: String, log: LoggingAdapter): Route = ((post | put) & entity(as[Json])) { event =>
    log.debug("Create event: {}, {}", engineId, event)
    completeByValidated(StatusCodes.Created) {
      (eventService ? CreateEvent(engineId, event.toString())).mapTo[Response]
    }
  }

}
