package com.actionml.authserver.routes

import akka.http.scaladsl.model.headers.HttpChallenges
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsMissing
import akka.http.scaladsl.server._
import com.actionml.authserver.exceptions.{AccessDeniedException, TokenExpiredException}
import com.actionml.authserver.service.AuthService
import com.actionml.authserver.{AuthorizationCheckRequest, Realms}
import com.actionml.circe.CirceSupport
import com.actionml.oauth2.entities.AccessTokenResponse.TokenTypes
import com.actionml.oauth2.entities.{AccessTokenResponse, PasswordAccessTokenRequest}
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Encoder, Json}
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}

class OAuth2Router(implicit injector: Injector) extends Directives with Injectable with CirceSupport with ClientAuthentication {
  private implicit val ec = inject[ExecutionContext]
  private val authService = inject[AuthService]

  def route: Route = handleExceptions(oAuthExceptionHandler) {
    (post & pathPrefix("auth") & authenticateClient(authService.authenticateClient)) { clientId =>
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
    authService.createAccessToken(request.username, request.password, clientId)
  }

  private def checkAuthorization: AuthorizationCheckRequest => Route = authCheckRequest => {
    import authCheckRequest._
    onSuccess(authService.authorize(accessToken, roleId, resourceId))(result => complete(Map("success" -> result).asJson))
  }

  implicit private val tokenTypesEncoder = Encoder.enumEncoder(TokenTypes)
}
