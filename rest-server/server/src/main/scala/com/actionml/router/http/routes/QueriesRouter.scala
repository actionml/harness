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
import cats.data.Validated
import com.actionml.authserver.Roles
import com.actionml.authserver.directives.AuthorizationDirectives
import com.actionml.authserver.service.AuthorizationService
import com.actionml.core.model.Response
import com.actionml.core.validate.ValidateError
import com.actionml.router.config.AppConfig
import com.actionml.router.service._
import org.json4s.JValue
import org.json4s.jackson.JsonMethods
import scaldi.Injector

/**
  *
  * Query endpoints:
  *
  * Add new event
  * PUT, POST /engines/<engine-id>/queries {JSON for PIO query}
  * Response: HTTP code 200 if the event was successfully;
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 25.02.17 11:10
  */
class QueriesRouter(implicit inj: Injector) extends BaseRouter with AuthorizationDirectives {
  override val authorizationService = inject[AuthorizationService]
  private val config = inject[AppConfig]
  override val authEnabled = config.auth.enabled
  private val queryService = inject[QueryService]('QueryService)

  override val route: Route = (rejectEmptyResponse & extractAccessToken) { implicit accessToken =>
    (pathPrefix("engines" / Segment) & extractLog) { (engineId, log) =>
      implicit val _ = log
      (pathPrefix("queries") & handleExceptions(exceptionHandler(log))) {
        pathEndOrSingleSlash {
          hasAccess(Roles.query.read, engineId).apply {
            getPrediction(engineId, log)
          }
        }
      }
    }
  }

  /** Creates a Query in REST so status = 201 */
  private def getPrediction(engineId: String, log: LoggingAdapter): Route = (post & entity(as[JValue])) { query =>
    log.debug("Receive query: {}", query)
    onSuccess(queryService.query(engineId, JsonMethods.compact(query))) { r => complete(r) }
//    completeByValidated(StatusCodes.Created) {
//      (queryService ? GetPrediction(engineId, JsonMethods.compact(query))).mapTo[Validated[ValidateError, Response]]
//    }
  }

  private def exceptionHandler(log: LoggingAdapter) = ExceptionHandler {
    case e =>
      log.error(e, "Internal error")
      complete(StatusCodes.InternalServerError)
  }
}
