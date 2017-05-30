package com.actionml.router.http.routes

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.actionml.router.service._
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
class QueriesRouter(implicit inj: Injector) extends BaseRouter {

  private val queryService = inject[ActorRef]('QueryService)

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

  private def getPrediction(engineId: String, log: LoggingAdapter): Route = asJson { query =>
    log.debug("Receive query: {}", query)
    completeByValidated(StatusCodes.OK) {
      (queryService ? GetPrediction(engineId, query.toString())).mapTo[Response]
    }
  }

}
