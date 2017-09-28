package com.actionml.authserver.routes

import akka.http.scaladsl.model.headers.HttpChallenges
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsMissing
import akka.http.scaladsl.server._
import com.actionml.authserver.codecs.Encoders._
import com.actionml.authserver.exceptions.{AccessDeniedException, TokenExpiredException}
import com.actionml.authserver.service.{AuthService, AuthorizationService}
import com.actionml.authserver.{AuthorizationCheckRequest, Realms}
import com.actionml.circe.CirceSupport
import com.actionml.oauth2.entities.AccessTokenResponse
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}

class OAuth2Router(implicit injector: Injector) extends Directives with Injectable with CirceSupport with AuthenticationDirectives {
  private implicit val ec = inject[ExecutionContext]
  private val authService = inject[AuthService]
  private val authorizationService = inject[AuthorizationService]

  def route: Route =
    (pathPrefix("auth") & handleExceptions(oAuthExceptionHandler)) {
      (path("token") & post & checkGrantType & basicAuth(authService.authenticateUser)) { username =>
        onSuccess(createToken(username))(token => complete(token))
      } ~
      (path("authorize") & post & entity(as[AuthorizationCheckRequest]) & basicAuth(authService.authenticateClient)) { (request, _) =>
        checkAuthorization(request)
      }
    }


  private def oAuthExceptionHandler = ExceptionHandler {
    case AccessDeniedException => reject(AuthorizationFailedRejection)
    case TokenExpiredException => complete(Json.obj("error" -> Json.fromString("token expired")))
  }

  private def checkGrantType: Directive0 = formFieldMap.flatMap { params =>
    if (params.get("grant_type").contains("client_credentials")) pass
    else reject(AuthenticationFailedRejection(CredentialsMissing, HttpChallenges.oAuth2(Realms.Harness)))
  }

  private def createToken(username: String): Future[AccessTokenResponse] = {
    authService.createAccessToken(username)
  }

  private def checkAuthorization: AuthorizationCheckRequest => Route = authCheckRequest => {
    import authCheckRequest._
    onSuccess(authorizationService.authorize(accessToken, roleId, resourceId))(result => complete(Map("success" -> result).asJson))
  }
}
