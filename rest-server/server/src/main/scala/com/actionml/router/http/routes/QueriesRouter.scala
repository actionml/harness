package com.actionml.router.http.routes

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.pattern.ask
import com.actionml.authserver.Roles
import com.actionml.authserver.directives.AuthorizationDirectives
import com.actionml.authserver.service.AuthorizationService
import com.actionml.router.config.AppConfig
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
class QueriesRouter(implicit inj: Injector) extends BaseRouter with AuthorizationDirectives {
  override val authorizationService = inject[AuthorizationService]
  private val config = inject[AppConfig]
  override val authEnabled = config.auth.enabled
  private val queryService = inject[ActorRef]('QueryService)

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

  private def getPrediction(engineId: String, log: LoggingAdapter): Route = (post & asJson) { query =>
    log.debug("Receive query: {}", query)
    completeByValidated(StatusCodes.OK) {
      (queryService ? GetPrediction(engineId, query.toString())).mapTo[Response]
    }
  }

  private def exceptionHandler(log: LoggingAdapter) = ExceptionHandler {
    case e =>
      log.error(e, "Internal error")
      complete(StatusCodes.InternalServerError)
  }
}
