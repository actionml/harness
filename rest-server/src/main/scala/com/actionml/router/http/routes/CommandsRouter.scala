package com.actionml.router.http.routes

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import io.circe.syntax._
import scaldi.Injector

/**
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  */
class CommandsRouter(implicit inj: Injector) extends BaseRouter {

  override val route: Route = rejectEmptyResponse {
    pathPrefix("commands" / "list" / Segment) { segment ⇒
      pathEndOrSingleSlash {
        segment match {
          case "engines" ⇒ getEngineList
          case "datasets" ⇒ getDatasetList
        }
      }
    }
  }

  private def getEngineList = (get & extractLog) { log ⇒
    log.info("Get engines list")
    complete(StatusCodes.OK, Seq.empty[String].asJson)
  }

  private def getDatasetList = (get & extractLog) { log ⇒
    log.info("Get datasets list")
    complete(StatusCodes.OK, Seq.empty[String].asJson)
  }

}
