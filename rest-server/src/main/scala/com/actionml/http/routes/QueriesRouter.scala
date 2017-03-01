package com.actionml.http.routes

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.actionml.cb.CBQueryResult
import com.actionml.service._
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import scaldi.Injector

import scala.language.postfixOps
import scala.util.Left

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
class QueriesRouter(implicit inj: Injector) extends BaseRouter {

  private val queryService = injectActorRef[QueryService]

  override val route: Route = rejectEmptyResponse {
    (pathPrefix("engines" / Segment) & extractLog) { (engineId, log) =>
      log.info("{}", engineId)
      pathPrefix("queries") {
        pathEndOrSingleSlash {
          getPrediction(engineId, log)
        }
      }
    }
  }

  private def getPrediction(engineId: String, log: LoggingAdapter): Route = ((post | put) & entity(as[Json])) { query =>
    log.debug("Receive query: {}", query)
    completeByCond(StatusCodes.OK) {
      (queryService ? GetPrediction(engineId, query.toString())).mapTo[Either[Int, CBQueryResult]].map(_.map(_.asJson))
    }
  }

}
