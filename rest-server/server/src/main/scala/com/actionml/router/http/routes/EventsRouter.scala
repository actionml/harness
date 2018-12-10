/*
 * Copyright ActionML, LLC under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * ActionML licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.actionml.router.http.routes

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.pattern.ask
import com.actionml.authserver.ResourceId
import com.actionml.authserver.Roles.event
import com.actionml.authserver.directives.AuthorizationDirectives
import com.actionml.authserver.service.AuthorizationService
import com.actionml.authserver.services.AuthServerProxyService
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
class EventsRouter(implicit inj: Injector) extends BaseRouter with AuthorizationDirectives {
  private val eventService = inject[ActorRef]('EventService)
  override val authorizationService = inject[AuthorizationService]
  private val config = inject[AppConfig]
  override val authEnabled = config.auth.enabled

  val route: Route = (rejectEmptyResponse & extractAccessToken) { implicit accessToken =>
    (pathPrefix("engines" / Segment) & extractLog) { (engineId, log) =>
      implicit val _ = log
      (pathPrefix("events") & handleExceptions(exceptionHandler(log))) {
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

  private def exceptionHandler(log: LoggingAdapter) = ExceptionHandler {
    case e =>
      log.error(e, "Internal error")
      complete(StatusCodes.InternalServerError)
  }

}
