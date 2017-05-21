package com.actionml.router.http.routes

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.actionml.router.service._
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
  * Update exist engine
  * PUT, POST /engines/<engine-id>?data_delete=true&force=true {JSON body for PIO event}
  * Response: HTTP code 200 if the engine was successfully updated; otherwise, 400.
  *
  * Get exist engine
  * GET /engines/<engine-id>
  * Response: {JSON body for PIO event}
  * HTTP code 200 if the engine exist; otherwise, 404
  *
  * Delete exist engine
  * DELETE /engines/<engine-id>
  * Response: HTTP code 200 if the engine was successfully deleted; otherwise, 400.
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 29.01.17 17:36
  */
class EnginesRouter(implicit inj: Injector) extends BaseRouter {

  private val engineService = inject[ActorRef]('EngineService)

  override val route: Route = rejectEmptyResponse {
    (pathPrefix("engines") & extractLog) { log ⇒
      pathEndOrSingleSlash {
        createEngine(log)
      } ~ pathPrefix(Segment) { engineId ⇒
        pathEndOrSingleSlash {
          getEngine(engineId, log) ~ updateEngine(engineId, log) ~ deleteEngine(engineId, log)
        }
      }
    }
  }

  private def getEngine(engineId: String, log: LoggingAdapter): Route = get {
    log.info("Get engine: {}", engineId)
    completeByValidated(StatusCodes.OK) {
      (engineService ? GetEngine(engineId)).mapTo[Response]
    }
  }

  private def createEngine(log: LoggingAdapter): Route = asJson { engineConfig =>
    log.info("Create engine: {}", engineConfig)
    completeByValidated(StatusCodes.Created) {
      (engineService ? CreateEngine(engineConfig.toString())).mapTo[Response]
    }
  }

  private def updateEngine(engineId: String, log: LoggingAdapter): Route = (putOrPost & parameters('data_delete.as[Boolean] ? false, 'force.as[Boolean] ? false) ) { (dataDelete, force) ⇒
    entity(as[Json]) { engineConfig ⇒
      log.info("Update engine: {}, {}, delete: {}, force: {}", engineId, engineConfig, dataDelete, force)
      completeByValidated(StatusCodes.OK) {
        (engineService ? UpdateEngineWithConfig(engineId, engineConfig.toString(), dataDelete, force)).mapTo[Response]
      }
    } ~ {
      log.info("Update engine: {}, delete: {}, force: {}", engineId, dataDelete, force)
      completeByValidated(StatusCodes.OK) {
        (engineService ? UpdateEngineWithId(engineId, dataDelete, force)).mapTo[Response]
      }
    }

  }

  private def deleteEngine(engineId: String, log: LoggingAdapter): Route = delete {
    log.info("Delete engine: {}", engineId)
    completeByValidated(StatusCodes.OK) {
      (engineService ? DeleteEngine(engineId)).mapTo[Response]
    }
  }

}
