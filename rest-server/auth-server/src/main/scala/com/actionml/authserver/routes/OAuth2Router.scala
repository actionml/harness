package com.actionml.authserver.routes

import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenges}
import akka.http.scaladsl.server.AuthenticationFailedRejection.{CredentialsMissing, CredentialsRejected}
import akka.http.scaladsl.server._
import com.actionml.authserver.exceptions.{AccessDeniedException, TokenExpiredException}
import com.actionml.authserver.model.Permission
import com.actionml.authserver.service.AuthService
import com.actionml.authserver.{AuthorizationCheckRequest, Realms}
import com.actionml.circe.CirceSupport
import com.actionml.oauth2.entities.AccessTokenResponse.TokenTypes
import com.actionml.oauth2.entities.{AccessTokenResponse, PasswordAccessTokenRequest}
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Encoder, Json}
import scaldi.Injector
import scaldi.akka.AkkaInjectable

import scala.concurrent.{ExecutionContext, Future}

class OAuth2Router(implicit injector: Injector) extends Directives with AkkaInjectable with CirceSupport {
  private implicit val ec = inject[ExecutionContext]
  private val authService = inject[AuthService]

  def route: Route = handleExceptions(oAuthExceptionHandler) {
    (post & pathPrefix("auth") & authenticateClient) { clientId =>
      (path("token") & extractTokenRequest) { request =>
        onSuccess(createToken(request, clientId))(token => complete(token))
      } ~
      (path("authorize") & entity(as[AuthorizationCheckRequest])) { checkAuthorization }
    }
  }


  private def oAuthExceptionHandler = ExceptionHandler {
    case AccessDeniedException => reject(AuthorizationFailedRejection)
    case TokenExpiredException => complete(Json.obj("error" -> Json.fromString("token expired")))
  }

  private def authenticateClient: Directive1[String] = extractCredentials.flatMap {
    case Some(BasicHttpCredentials(clientId, password)) =>
      onSuccess(authService.authenticateClient(clientId, password).map(_ => clientId))
    case None =>
      reject(new AuthenticationFailedRejection(CredentialsMissing, HttpChallenges.basic(Realms.Harness))): Directive1[String]
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

  private def createToken(request: PasswordAccessTokenRequest, clientId: String): Future[AccessTokenResponse] = {
    val permissions = Seq(Permission(clientId, Map.empty))
    authService.createAccessToken(request.username, request.password, permissions)
  }

  private def checkAuthorization: AuthorizationCheckRequest => Route = authCheckRequest => {
    import authCheckRequest._
    onSuccess(authService.authorize(accessToken, roleId, resourceId))(result => complete(Map("success" -> result).asJson))
  }

  implicit private val tokenTypesEncoder = Encoder.enumEncoder(TokenTypes)
}
