package com.actionml.security.routes

import akka.http.scaladsl.server.{Directives, Route}
import com.actionml.security.model.{AccessToken, Permission}
import com.actionml.security.service.AuthServiceComponent
import de.heikoseeberger.akkahttpcirce._
import io.circe.generic.auto._
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

case class AuthenticationRequest(token: String)
case class AuthenticationResponse(accessToken: String, ttl: Option[Long], refreshToken: Option[String])
case class AuthorizationRequest(token: String, role: String, resourceId: String)
case class AuthorizationResponse(success: Boolean)

/**
  * /POST /auth/api/v1/authenticate
  * Request body: {“token”: “string”}
  * Response: HTTP code 200 and body {“accessToken”: “string”, “ttl”: Option[Long], “refreshToken”: “optional string”}
  *   if authentication was successful; otherwise, HTTP code 401.
  *
  * /POST /auth/api/v1/authorize
  * Request body: {“accessToken”: “string”, “roleId”: ”string”, “resourceId”: “string”}
  * Response: HTTP code 200 and body {“success”: “true”} if authorization succeed; otherwise, HTTP code 403.
  */
class SecurityController extends Directives with CirceSupport {
  this: AuthServiceComponent =>

  def route: Route = (pathPrefix("auth" / "v1") & post) {
    (path("authenticate") & entity(as[AuthenticationRequest]))(authenticate) ~
    (path("authorize") & entity(as[AuthorizationRequest]))(authorize)
  }

  private def authenticate: AuthenticationRequest => Route = request => {
    onSuccess(authService.authenticate(request.token)) { accessToken =>
      complete(accessToken.asJson)
    }
  }

  private def authorize: AuthorizationRequest => Route = authorizationRequest => {
    import authorizationRequest.{token, role, resourceId}
    onSuccess(authService.authorize(token, role, resourceId)) { result =>
      complete(AuthorizationResponse(result).asJson)
    }
  }

  // workaround for https://github.com/circe/circe/issues/251 - remove after close
  implicit private val permissionEncoder: Encoder[Permission] = Encoder.instance {
    p: Permission => p.asJson
  }
  implicit private val accessTokenEncoder: Encoder[AccessToken] = Encoder.instance {
    p: AccessToken => p.asJson
  }
  // end workaround
}
