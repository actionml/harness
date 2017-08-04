package com.actionml.router.http.routes

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.pattern.ask
import com.actionml.router.config.{AppConfig, ConfigurationComponent}
import com.actionml.router.service._
import com.actionml.security.Roles.engine
import com.actionml.security.directives.AuthDirectives
import com.actionml.security.model.{ResourceId, Secret}
import com.actionml.security.services.{AuthService, AuthServiceComponent}
import io.circe.Json
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
class EnginesRouter(implicit inj: Injector)
  extends BaseRouter
    with AuthDirectives
    with SecurityDirectives
    with ConfigurationComponent
    with AuthServiceComponent {

  private val engineService = inject[ActorRef]('EngineService)
  override val authService = inject[AuthService]
  override val config = inject[AppConfig]

  override val route: Route = (rejectEmptyResponse & extractOauth2Credentials) { implicit credentials =>
    (pathPrefix("engines") & extractLog) { implicit log =>
      (pathEndOrSingleSlash & authorizeUser(engine.modify, ResourceId.*)) {
        createEngine
      } ~
      path(Segment) { engineId ⇒
        authorizeUser(engine.read, engineId).apply {
          getEngine(engineId)
        } ~
        authorizeUser(engine.modify, engineId).apply {
          updateEngine(engineId) ~
          deleteEngine(engineId)
        }
      }
    }
  }

  private def getEngine(engineId: String)(implicit log: LoggingAdapter): Route = get {
    log.info("Get engine: {}", engineId)
    completeByValidated(StatusCodes.OK) {
      (engineService ? GetEngine(engineId)).mapTo[Response]
    }
  }

  private def createEngine(implicit log: LoggingAdapter): Route = asJson { engineConfig =>
    log.info("Create engine: {}", engineConfig)
    completeByValidated(StatusCodes.Created) {
      (engineService ? CreateEngine(engineConfig.toString())).mapTo[Response]
    }
  }


  private def updateEngine(engineId: String)(implicit log: LoggingAdapter): Route = (putOrPost & parameters('data_delete.as[Boolean] ? false, 'force.as[Boolean] ? false, 'input.as[String]) ) { (dataDelete, force, input) ⇒
    entity(as[Json]) { engineConfig ⇒
      //log.info("Update engine: {}, {}, delete: {}, force: {}, input: {}", engineId, engineConfig, dataDelete, force, input)
      log.info("Update engine: {}, {}, delete: {}, force: {}", engineId, engineConfig, dataDelete, force)
      completeByValidated(StatusCodes.OK) {
        (engineService ? UpdateEngineWithConfig(engineId, engineConfig.toString(), dataDelete, force, input)).mapTo[Response]
      }
    } ~ {
      log.info("Update engine: {}, delete: {}, force: {}, input: {}", engineId, dataDelete, force, input)
      completeByValidated(StatusCodes.OK) {
        (engineService ? UpdateEngineWithId(engineId, dataDelete, force, input)).mapTo[Response]
      }
    }

  }


  private def deleteEngine(engineId: String)(implicit log: LoggingAdapter): Route = delete {

    log.info("Delete engine: {}", engineId)
    completeByValidated(StatusCodes.OK) {
      (engineService ? DeleteEngine(engineId)).mapTo[Response]
    }
  }

}
