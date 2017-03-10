package com.actionml.router.http

import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.server.{Directives, Route}
import com.actionml.oauth2.OAuth2DataHandler
import com.actionml.oauth2.entities.Account
import scaldi.Injector
import scaldi.akka.AkkaInjectable

import scalaoauth2.provider.AuthInfo

/**
  * ⓭ + 15
  * Какой сам? by Pavlenov Semen 22.01.17.
  */
class OAuthRoutes(implicit inj: Injector) extends Directives with OAuth2RouteProvider[Account] with AkkaInjectable{

  override val oauth2DataHandler: OAuth2DataHandler = inject[OAuth2DataHandler]

  def protectedRoute: Route = path("resources") {
    get {
      authenticateOAuth2Async[AuthInfo[Account]]("realm", oauth2Authenticator) {
        auth ⇒ complete(OK, s"Hello ${auth.clientId.getOrElse("")}")
      }
    }
  }

  val routes: Route = oauthRoute ~ protectedRoute

}
