package com.actionml.http

import akka.http.scaladsl.model.StatusCodes.{InternalServerError, Unauthorized}
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.CirceSupport
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}
import scalaoauth2.provider._

/**
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  */
trait OAuth2RouteProvider[U] extends Directives with CirceSupport{

  val oauth2DataHandler : DataHandler[U]

  val tokenEndpoint = new TokenEndpoint {
    override val handlers = Map(
      OAuthGrantType.CLIENT_CREDENTIALS -> new ClientCredentials,
      OAuthGrantType.PASSWORD -> new Password,
      OAuthGrantType.AUTHORIZATION_CODE -> new AuthorizationCode,
      OAuthGrantType.REFRESH_TOKEN -> new RefreshToken
    )
  }

  def grantResultToTokenResponse(grantResult : GrantHandlerResult[U]) : TokenResponse =
    TokenResponse(grantResult.tokenType, grantResult.accessToken, grantResult.expiresIn.getOrElse(1L), grantResult.refreshToken.getOrElse(""))

  def oauth2Authenticator(credentials: Credentials): Future[Option[AuthInfo[U]]] =
    credentials match {
      case p@Credentials.Provided(token) ⇒
        oauth2DataHandler.findAccessToken(token).flatMap {
          case Some(accessToken) ⇒ oauth2DataHandler.findAuthInfoByAccessToken(accessToken)
          case None ⇒ Future.successful(None)
        }
      case _ ⇒ Future.successful(None)
    }

  def accessTokenRoute: Route = pathPrefix("oauth") {
    path("access_token") {
      post {
        formFieldMap { fields ⇒
          println("Fields: ", fields)
          onComplete(tokenEndpoint.handleRequest(new AuthorizationRequest(Map(), fields.map(m ⇒ m._1 -> Seq(m._2))), oauth2DataHandler)) {
            case Success(maybeGrantResponse) ⇒
              println("MaybeGrantResponse: ", maybeGrantResponse)
              maybeGrantResponse.fold(oauthError ⇒ complete(Unauthorized),
                grantResult ⇒ complete(grantResultToTokenResponse(grantResult))
              )
            case Failure(ex) ⇒ complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
          }
        }
      }
    }
  }

}

case class TokenResponse(token_type : String, access_token : String, expires_in : Long, refresh_token : String)
