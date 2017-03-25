package com.actionml.router.http.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import scaldi.Injector

/**
  * Created by semen on 03.03.17.
  */
class CheckRouter(implicit inj: Injector) extends BaseRouter {
  override val route: Route = pathSingleSlash {
    get {
      complete(StatusCodes.OK)
    }
  }
}
