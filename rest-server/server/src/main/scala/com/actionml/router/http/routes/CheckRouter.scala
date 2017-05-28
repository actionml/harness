package com.actionml.router.http.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import scaldi.Injector

/**
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  */
class CheckRouter(implicit inj: Injector) extends BaseRouter {
  override val route: Route = pathSingleSlash {
    get {
      complete(StatusCodes.OK)
    }
  }
}
