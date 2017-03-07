package com.actionml.router.http

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes.{InternalServerError, Unauthorized}
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.CirceSupport
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.{Failure, Success}
import scalaoauth2.provider._

/**
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  */
trait OAuth2RouteProvider[U] extends Directives with CirceSupport {

  val oauth2DataHandler: DataHandler[U]

  val tokenEndpoint = new TokenEndpoint {
    override val handlers = Map(
      OAuthGrantType.CLIENT_CREDENTIALS -> new ClientCredentials,
      OAuthGrantType.PASSWORD -> new Password,
      OAuthGrantType.AUTHORIZATION_CODE -> new AuthorizationCode,
      OAuthGrantType.REFRESH_TOKEN -> new RefreshToken
    )
  }

  def oauth2Authenticator(credentials: Credentials): Future[Option[AuthInfo[U]]] =
    credentials match {
      case p@Credentials.Provided(token) ⇒
        oauth2DataHandler.findAccessToken(token).flatMap {
          case Some(accessToken) ⇒ oauth2DataHandler.findAuthInfoByAccessToken(accessToken)
          case None ⇒ Future.successful(None)
        }
      case _ ⇒ Future.successful(None)
    }


  def oauthRoute: Route = (pathPrefix("oauth") & post & formFieldMultiMap & extractRequest & extractLog) { (params, request, log) ⇒

    log.info("Params: {}", params)
    log.info("Headers: {}", request.headers)
    val headers = request.headers.map(h ⇒ h.name() → Seq(h.value())).toMap
    val authorizationRequest = new AuthorizationRequest(headers, params)
    log.info("GrantType {}", authorizationRequest.grantType)
    log.info("Headers {}", authorizationRequest.headers)
    log.info("ClientCredential {}", authorizationRequest.parseClientCredential)

    accessTokenRouter(authorizationRequest) ~ refreshTokenRouter(authorizationRequest)
  }

  /**
    * https://tools.ietf.org/html/rfc6749#section-4.4
    * @param authorizationRequest
    * @return
    */
  def accessTokenRouter(authorizationRequest: AuthorizationRequest): Route = (path("access_token") & extractLog) { log ⇒
    log.info("AccessToken {}", authorizationRequest)
    sendRequest(authorizationRequest, log)
  }

  /**
    * https://tools.ietf.org/html/rfc6749#section-6
    * @param authorizationRequest
    * @return
    */
  def refreshTokenRouter(authorizationRequest: AuthorizationRequest): Route = (path("refresh_token") & extractLog) { log ⇒
    val refreshTokenRequest = RefreshTokenRequest(authorizationRequest)
    log.info("RefreshToken {}", refreshTokenRequest.refreshToken)
    sendRequest(refreshTokenRequest, log)
  }

  private def sendRequest(authorizationRequest: AuthorizationRequest, log: LoggingAdapter) = {
    onComplete(tokenEndpoint.handleRequest(authorizationRequest, oauth2DataHandler)) {
      case Success(maybeGrantResponse) ⇒
        log.info("MaybeGrantResponse: {}", maybeGrantResponse)
        maybeGrantResponse.fold(
          oauthError ⇒ {
            log.error(oauthError, "Request error")
            complete(Unauthorized)
          },
          grantResult ⇒ complete(grantResultToTokenResponse(grantResult))
        )
      case Failure(ex) ⇒ complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
    }
  }

  def grantResultToTokenResponse(grantResult: GrantHandlerResult[U]): TokenResponse =
    TokenResponse(grantResult.tokenType, grantResult.accessToken, grantResult.expiresIn.getOrElse(0L), grantResult.refreshToken.getOrElse(""))

}

case class AuthRequest(client_id: String, client_secret: String, grant_type: String, scope: Option[Seq[String]]) {
  def toMap: Map[String, Seq[String]] = {
    Map(
      "client_id" → Seq(client_id),
      "client_secret" → Seq(client_secret),
      "grant_type" → Seq(grant_type),
      "scope" → scope.getOrElse(Seq.empty)
    )
  }
}

case class TokenResponse(token_type: String, access_token: String, expires_in: Long, refresh_token: String)
