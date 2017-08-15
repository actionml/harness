package com.actionml.authserver.routes

import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenges}
import akka.http.scaladsl.server.AuthenticationFailedRejection.{CredentialsMissing, CredentialsRejected}
import akka.http.scaladsl.server._
import com.actionml.authserver.model.Client
import com.actionml.authserver.service.AuthService
import com.actionml.authserver.{AuthorizationCheckRequest, Realms}
import com.actionml.oauth2.entities.{AccessTokenResponse, PasswordAccessTokenRequest}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Encoder
import io.circe.generic.auto._
import io.circe.syntax._
import scaldi.Injector
import scaldi.akka.AkkaInjectable

import scala.concurrent.{ExecutionContext, Future}

class AuthorizationController(implicit injector: Injector) extends Directives with AkkaInjectable with FailFastCirceSupport {
  private implicit val ec = inject[ExecutionContext]
  private val authService = inject[AuthService]

  def route: Route = (post & pathPrefix("auth") & authenticateClient) { _ =>
    (path("token") & extractTokenRequest) { request => onSuccess(createToken(request)) { token => complete(token.asJson)} } ~
    (path("authorize") & entity(as[AuthorizationCheckRequest])) { checkAuthorization }
  }

  private def authenticateClient: Directive1[Client] = extractCredentials.flatMap {
    case Some(BasicHttpCredentials(name, password)) => onSuccess(authService.authenticateClient(name, password))
    case None => reject(new AuthenticationFailedRejection(CredentialsMissing, HttpChallenges.basic(Realms.Harness))): Directive1[Client]
  }.recover(_ => reject(new AuthenticationFailedRejection(CredentialsRejected, HttpChallenges.basic(Realms.Harness))))

  private def extractTokenRequest: Directive1[PasswordAccessTokenRequest] = formFieldMap.flatMap { params =>
    (for {
      grantType <- params.get("grant_type") if grantType == "password"
      username <- params.get("username")
      password <- params.get("password")
    } yield PasswordAccessTokenRequest(username, password, scope = None)).fold(
      reject(AuthenticationFailedRejection(CredentialsMissing, HttpChallenges.oAuth2(Realms.Harness))): Directive1[PasswordAccessTokenRequest]
    )(provide)
  }

  private def createToken: PasswordAccessTokenRequest => Future[AccessTokenResponse] = request => {
    import request.{password, username}
    authService.authenticateUser(username, password).flatMap { _ =>
      authService.createAccessToken(username, ???)
    }
  }

  private def checkAuthorization: AuthorizationCheckRequest => Route = authCheckRequest => {
    import authCheckRequest._
    onSuccess(authService.authorize(accessToken, roleId, resourceId))(result => complete(Map("success" -> result).asJson))
  }

  implicit val accessTokenResponseEncoder: Encoder[AccessTokenResponse] = Encoder.instance(a => a.asJson)
}
