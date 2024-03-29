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
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import cats.data.Validated
import cats.data.Validated.Valid
import com.actionml.admin.Administrator
import com.actionml.authserver.{AccessToken, ResourceId}
import com.actionml.authserver.Roles.{engine, system}
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
  * Engine endpoints:
  *
  * Add new engine
  * PUT, POST /engines/ {JSON body for PIO engine}
  * Response: HTTP code 201 if the engine was successfully created; otherwise, 400.
  *
  * Todo: Update existing engine
  * PUT, POST /engines/<engine-id>?data_delete=true&force=true {JSON body for PIO event}
  * Response: HTTP code 200 if the engine was successfully updated; otherwise, 400.
  *
  * Update existing engine
  * PUT, POST /engines/<engine-id>?import=true&location=String
  * Response: HTTP code 200 if the engine was successfully updated; otherwise, 400.
  *
  * Get existing engine
  * GET /engines/<engine-id>
  * Response: {JSON body for PIO event}
  * HTTP code 200 if the engine exist; otherwise, 404
  *
  * Delete existing engine
  * DELETE /engines/<engine-id>
  * Response: HTTP code 200 if the engine was successfully deleted; otherwise, 400.
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 29.01.17 17:36
  */
class EnginesRouter(implicit inj: Injector) extends BaseRouter with AuthorizationDirectives {
  private val engineService = inject[ActorRef]('EngineService)
  override val authorizationService = inject[AuthorizationService]
  private val config = inject[AppConfig]
  private val admin = inject[Administrator]
  override val authEnabled = config.auth.enabled

  override val route: Route = (rejectEmptyResponse & extractAccessToken & extractLog) { (accessToken, log) => {
    implicit val l: LoggingAdapter = log
    implicit val at: Option[AccessToken] = accessToken
    pathPrefix("engines") {
      (pathEndOrSingleSlash & hasAccess(engine.create, ResourceId.*)) {
        getEngines ~
          createEngine
      } ~
        pathPrefix(Segment) { engineId ⇒
          hasAccess(engine.read, engineId).apply {
            pathEndOrSingleSlash(getEngine(engineId))
          } ~
            hasAccess(engine.modify, engineId).apply {
              (pathEndOrSingleSlash & delete) (deleteEngine(engineId)) ~
                (path("imports") & post) (updateEngineWithImport(engineId)) ~
                (path("configs") & post) (updateEngineWithConfig(engineId)) ~
                pathPrefix("jobs") {
                  (pathEndOrSingleSlash & post) (updateEngineWithTrain(engineId)) ~
                    (path(Segment) & delete) { jobId =>
                      cancelJob(engineId, jobId)
                    }
                } ~
                pathPrefix("entities") {
                  path(Segment) { userId =>
                    get(getUserData(engineId, userId)) ~
                      delete(deleteUserData(engineId, userId))
                  }
                }
            }
        }
    } ~
    (pathPrefix("system") & hasAccess(system.info)) {
      path("health")(healthCheck) ~
      pathEndOrSingleSlash(getSystemInfo)
    }
  }}


  private def healthCheck: Route = get {
    completeByValidated(StatusCodes.OK) {
      admin.healthCheck.map(Valid(_))
    }
  }

  private def getSystemInfo(implicit log: LoggingAdapter): Route = get {
    log.info("Get system info")
    completeByValidated(StatusCodes.OK) {
      (engineService ? GetSystemInfo()).mapTo[Validated[ValidateError, Response]]
    }
  }

  private def getEngine(engineId: String)(implicit log: LoggingAdapter): Route = get {
    log.info("Get engine: {}", engineId)
    completeByValidated(StatusCodes.OK) {
      (engineService ? GetEngine(engineId)).mapTo[Validated[ValidateError, Response]]
    }
  }

  private def getEngines(implicit log: LoggingAdapter): Route = get {
    log.info("Get engines information")
    completeByValidated(StatusCodes.OK) {
      (engineService ? GetEngines).mapTo[Validated[ValidateError, List[Response]]]
    }
  }

  private def createEngine(implicit log: LoggingAdapter): Route = entity(as[JValue]) { engineConfig =>

    log.info("Create engine: {}", engineConfig)
    completeByValidated(StatusCodes.Created) {
      (engineService ? CreateEngine(JsonMethods.compact(engineConfig))).mapTo[Validated[ValidateError, Response]]
    }
  }

  private def updateEngineWithConfig(engineId: String)(implicit log: LoggingAdapter): Route = entity(as[JValue]) { engineConfig ⇒
    log.info("Update engine: {}, updateConfig: true", engineId)
    completeByValidated(StatusCodes.OK) {
      (engineService ? UpdateEngine(JsonMethods.compact(engineConfig))).mapTo[Validated[ValidateError, Response]]
    }
  }

  private def updateEngineWithImport(engineId: String)(implicit log: LoggingAdapter): Route = parameter('import_path) { importPath ⇒
    log.info("Update engine: {}, importPath: {}", engineId, importPath)
    completeByValidated(StatusCodes.OK) {
      (engineService ? UpdateEngineWithImport(engineId, importPath)).mapTo[Validated[ValidateError, Response]]
    }
  }

  private def updateEngineWithTrain(engineId: String)(implicit log: LoggingAdapter): Route = {
    log.info("Update engine: {}, trainPath: {}", engineId)
    completeByValidated(StatusCodes.OK) {
      (engineService ? UpdateEngineWithTrain(engineId)).mapTo[Validated[ValidateError, Response]]
    }
  }

  private def deleteEngine(engineId: String)(implicit log: LoggingAdapter): Route = {
    log.info("Delete engine: {}", engineId)
    completeByValidated(StatusCodes.OK) {
      (engineService ? DeleteEngine(engineId)).mapTo[Validated[ValidateError, Response]]
    }
  }

  private def cancelJob(engineId: String, jobId: String)(implicit log: LoggingAdapter): Route = {
    log.info(s"Cancel job $jobId")
    completeByValidated(StatusCodes.OK) {
      (engineService ? CancelJob(engineId, jobId)).mapTo[Validated[ValidateError, Response]]
    }
  }

  private def getUserData(engineId: String, userId: String) = parameters('num.as[Int].?, 'from.as[Int].?) { (num, from) =>
    completeByValidated(StatusCodes.OK) {
      (engineService ? GetUserData(engineId, userId, num = num.getOrElse(100), from = from.getOrElse(0)))
        .mapTo[Validated[ValidateError, Response]]
    }
  }

  private def deleteUserData(engineId: String, userId: String) = {
    completeByValidated(StatusCodes.OK) {
      (engineService ? DeleteUserData(engineId, userId)).mapTo[Validated[ValidateError, Response]]
    }
  }

}
