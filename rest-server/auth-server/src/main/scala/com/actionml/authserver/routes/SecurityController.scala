package com.actionml.authserver.routes

import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.server.{Directives, Route}
import com.actionml.authserver.AuthorizationCheckRequest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import scaldi.Injector
import scaldi.akka.AkkaInjectable

class SecurityController(implicit injector: Injector) extends Directives with AkkaInjectable with FailFastCirceSupport {
  import io.circe.generic.auto._

  def route: Route = (post & pathPrefix("auth") & extractCredentials) { clientCredentials =>
    (path("token") & formFieldMap) { token(clientCredentials) } ~
    (path("authorize") & entity(as[AuthorizationCheckRequest])) { checkAuthorization(clientCredentials) }
  }

  private def token: Option[HttpCredentials] => Map[String, String] => Route = userParams => {
    ???
  }

  private def checkAuthorization: Option[HttpCredentials] => AuthorizationCheckRequest => Route = authCheckRequest => {
    ???
  }
}
