package com.actionml.router.http.routes

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.actionml.router.service._
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import cats.syntax.either._
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
  * PUT, POST /engines/<engine-id> {JSON body for PIO event}
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

  private val engineService = injectActorRef[EngineService]

  val route: Route = rejectEmptyResponse {
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
    complete((engineService ? GetEngine(engineId))
      .mapTo[Either[Int, Boolean]]
      .map(_.map(_.asJson))
    )
  }

  private def createEngine(log: LoggingAdapter): Route = (putOrPost & entity(as[Json])) { engine =>
    log.info("Create event: {}", engine)
    complete()
//    completeByCond(StatusCodes.Created) {
//      (engineService ? CreateEngine(engine.toString())).mapTo[Either[Int, Boolean]].map(_.map(_.asJson))
//    }
  }

  private def updateEngine(engineId: String, log: LoggingAdapter): Route = (putOrPost & entity(as[Json])) { engine =>
    log.info("Update engine: {}, {}", engineId, engine)
    complete()
//    complete((engineService ? UpdateEngine(engineId, engine.toString()))
//      .mapTo[Either[Int, Boolean]]
//      .map(_.map(_.asJson))
//    )
  }

  private def deleteEngine(engineId: String, log: LoggingAdapter): Route = delete {
    log.info("Update engine: {}", engineId)
    complete()
//    complete((engineService ? DeleteEngine(engineId))
//      .mapTo[Either[Int, Boolean]]
//      .map(_.map(_.asJson))
//    )
  }

}
