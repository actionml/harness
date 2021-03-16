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

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.stream.ActorMaterializer
import cats.data.Validated.{Invalid, Valid}
import com.actionml.authserver.Roles
import com.actionml.authserver.directives.AuthorizationDirectives
import com.actionml.authserver.service.AuthorizationService
import com.actionml.core.config.{AppConfig, AuthConfig}
import com.actionml.core.validate.ValidRequestExecutionError
import com.actionml.router.service._
import com.typesafe.scalalogging.LazyLogging
import org.json4s.JValue
import org.json4s.jackson.JsonMethods

import scala.concurrent.ExecutionContext

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
class QueriesRouter(
  override val authorizationService: AuthorizationService,
  queryService: QueryService)(
  implicit val actorSystem: ActorSystem,
  implicit protected val executor: ExecutionContext,
  implicit protected val materializer: ActorMaterializer,
  implicit val config: AppConfig
) extends BaseRouter with AuthorizationDirectives with LazyLogging {

  override val authEnabled: Boolean = config.auth.enabled

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
    completeByValidated(StatusCodes.Created) {
      queryService.queryAsync(engineId, JsonMethods.compact(query))
        .map(Valid(_))
        .recover { case e =>
          logger.error("Prediction error", e)
          Invalid(ValidRequestExecutionError("Prediction error"))
        }
    }
  }

  private def exceptionHandler(log: LoggingAdapter) = ExceptionHandler {
    case e =>
      log.error(e, "Internal error")
      complete(StatusCodes.InternalServerError)
  }
}
