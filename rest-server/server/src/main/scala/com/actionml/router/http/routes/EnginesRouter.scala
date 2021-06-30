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
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.actionml.authserver.ResourceId
import com.actionml.authserver.Roles.engine
import com.actionml.authserver.directives.AuthorizationDirectives
import com.actionml.authserver.service.AuthorizationService
import com.actionml.core.config.AppConfig
import com.actionml.router.service._
import org.json4s.JValue
import org.json4s.jackson.JsonMethods

import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

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
class EnginesRouter(
  engineService: EngineServiceImpl,
  override val authorizationService: AuthorizationService)(
  implicit val actorSystem: ActorSystem,
  implicit protected val executor: ExecutionContext,
  implicit protected val materializer: ActorMaterializer,
  implicit val config: AppConfig
) extends BaseRouter with AuthorizationDirectives {

  override val authEnabled: Boolean = config.auth.enabled

  override val route: Route = (rejectEmptyResponse & extractAccessToken) { implicit accessToken =>
    (pathPrefix("engines") & extractLog) { implicit log =>
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
            (pathEndOrSingleSlash & post)(updateEngineWithTrain(engineId)) ~
            (path(Segment) & delete) { jobId =>
              cancelJob(engineId, jobId)
            }
          } ~
          pathPrefix("entities") {
            path(Segment) { userId =>
              get (getUserData(engineId, userId)) ~
              delete (deleteUserData(engineId, userId))
            }
          }
        }
      }
    }
  }


  private def getSystemInfo(implicit log: LoggingAdapter): Route = get {
    log.info("Get system info")
    completeByValidated(StatusCodes.OK) {
      engineService.getSystemInfo
    }
  }

  private def getEngine(engineId: String)(implicit log: LoggingAdapter): Route = get {
    log.info("Get engine: {}", engineId)
    completeByValidated(StatusCodes.OK) {
      engineService.status(engineId)
    }
  }

  private def getEngines(implicit log: LoggingAdapter): Route = get {
    log.info("Get engines information")
    completeByValidated(StatusCodes.OK) {
      engineService.statuses()
    }
  }

  private def createEngine(implicit log: LoggingAdapter): Route = entity(as[JValue]) { engineConfig =>

    log.info("Create engine: {}", engineConfig)
    completeByValidated(StatusCodes.Created) {
      engineService.addEngine(JsonMethods.compact(engineConfig))
    }
  }

  private def updateEngineWithConfig(engineId: String)(implicit log: LoggingAdapter): Route = entity(as[JValue]) { engineConfig ⇒
    log.info("Update engine: {}, updateConfig: true", engineId)
    completeByValidated(StatusCodes.OK) {
      engineService.updateEngine(JsonMethods.compact(engineConfig))
    }
  }

  private def updateEngineWithImport(engineId: String)(implicit log: LoggingAdapter): Route = parameter('import_path) { importPath ⇒
    log.info("Update engine: {}, importPath: {}", engineId, importPath)
    completeByValidated(StatusCodes.OK) {
      engineService.importFromPath(engineId, importPath)
    }
  }

  private def updateEngineWithTrain(engineId: String)(implicit log: LoggingAdapter): Route = {
    log.info("Update engine: {}, trainPath: {}", engineId)
    completeByValidated(StatusCodes.OK) {
      engineService.train(engineId)
    }
  }

  private def deleteEngine(engineId: String)(implicit log: LoggingAdapter): Route = {
    log.info("Delete engine: {}", engineId)
    completeByValidated(StatusCodes.OK) {
      engineService.deleteEngine(engineId)
    }
  }

  private def cancelJob(engineId: String, jobId: String)(implicit log: LoggingAdapter): Route = {
    log.info(s"Cancel job $jobId")
    completeByValidated(StatusCodes.OK) {
      engineService.cancelJob(engineId, jobId)
    }
  }

  private def getUserData(engineId: String, userId: String) = parameters('num.as[Int].?, 'from.as[Int].?) { (num, from) =>
    completeByValidated(StatusCodes.OK) {
      engineService.getUserData(engineId, userId, num.getOrElse(100), from.getOrElse(0))
    }
  }

  private def deleteUserData(engineId: String, userId: String) = {
    completeByValidated(StatusCodes.OK) {
      engineService.deleteUserData(engineId, userId)
    }
  }

}
