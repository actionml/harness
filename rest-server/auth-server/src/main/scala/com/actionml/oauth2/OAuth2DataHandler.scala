package com.actionml.oauth2

import akka.event.LoggingAdapter
import com.actionml.oauth2.dal.{AccountsDal, OAuthAccessTokensDal, OAuthAuthorizationCodesDal, OAuthClientsDal}
import com.actionml.oauth2.entities.{Account, OAuthAccessToken}
import scaldi.Injector
import scaldi.akka.AkkaInjectable

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions
import scalaoauth2.provider._

/**
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  */

class OAuth2DataHandler(implicit inj: Injector) extends DataHandler[Account] with AkkaInjectable{

  private val log: LoggingAdapter = inject[(Class[_]) ⇒ LoggingAdapter].apply(this.getClass)

  val accountsDal: AccountsDal = inject[AccountsDal]
  val oauthClientsDal: OAuthClientsDal = inject[OAuthClientsDal]
  val oauthAccessTokensDal: OAuthAccessTokensDal = inject[OAuthAccessTokensDal]
  val oauthAuthorizationCodesDal: OAuthAuthorizationCodesDal = inject[OAuthAuthorizationCodesDal]

  private val accessTokenExpireSeconds: Int = 3600

  private def toAccessToken(accessToken: OAuthAccessToken): AccessToken = {
    AccessToken(
      token = accessToken.accessToken,
      refreshToken = Some(accessToken.refreshToken),
      scope = None,
      lifeSeconds  = Some(accessTokenExpireSeconds),
      createdAt = accessToken.createdAt.toDate
    )
  }

  private def getAuthInfo(accountId: String, clientId: String) = {
    for {
      maybeAccount <- accountsDal.findByAccountId(accountId)
      maybeClient <- oauthClientsDal.findByClientId(clientId)
    } yield for {
      account <- maybeAccount
      client <- maybeClient
    } yield AuthInfo(
      user = account,
      clientId = Some(client.id),
      scope = None,
      redirectUri = client.redirectUri
    )
  }

  override def validateClient(
    maybeCredential: Option[ClientCredential],
    request: AuthorizationRequest
  ): Future[Boolean] = {
    log.info("ValidateClient - ClientCredential: {}, GrantType: {}", maybeCredential, request.grantType)
    val grantType = request.grantType
    maybeCredential match {
      case Some(ClientCredential(clientId, Some(clientSecret))) ⇒ oauthClientsDal.validate(
        clientId = clientId,
        clientSecret = clientSecret,
        grantType = grantType
      )
      case _ ⇒ Future.successful(false)
    }
  }

  override def findUser(
    maybeCredential: Option[ClientCredential],
    request: AuthorizationRequest
  ): Future[Option[Account]] = {
    log.info("FindUser: {}, {}", maybeCredential, request)
    request match {

      case request: PasswordRequest ⇒
        log.info("PasswordRequest {}, {}", request.username, request.password)
        accountsDal.authenticate(request.username, request.password)

      case _: ClientCredentialsRequest ⇒
        log.info("ClientCredentialsRequest")
        maybeCredential match {
          case Some(ClientCredential(clientId, Some(clientSecret))) ⇒ oauthClientsDal.findClientCredentials(
            clientId = clientId,
            clientSecret = clientSecret
          )
          case _ ⇒ Future.failed[Option[Account]](new InvalidRequest())
        }

      case _ ⇒ Future.successful(None)

    }
  }

  override def createAccessToken(authInfo: AuthInfo[Account]): Future[AccessToken] = {
    log.info("CreateAccessToken {}", authInfo)
    authInfo match {
      case AuthInfo(account, Some(clientId), maybeScope, maybeRedirectUri) ⇒ (for {
        maybeClient <- oauthClientsDal.findByClientId(clientId)
        toAccessToken <- oauthAccessTokensDal.create(authInfo.user, maybeClient.get).map(toAccessToken) if maybeClient.isDefined
      } yield toAccessToken).recover { case _ ⇒ throw new InvalidRequest() }
      case _ ⇒ Future.failed[AccessToken](new InvalidRequest())
    }
  }

  override def getStoredAccessToken(authInfo: AuthInfo[Account]): Future[Option[AccessToken]] = {
    authInfo match {
      case AuthInfo(account, Some(clientId), maybeScope, maybeRedirectUri) ⇒ oauthAccessTokensDal.findByAuthorized(
        account = account,
        clientId = clientId
      ).map(_.map(toAccessToken))
      case _ ⇒ Future.successful(None)
    }
  }

  override def refreshAccessToken(
    authInfo: AuthInfo[Account],
    refreshToken: String
  ): Future[AccessToken] = {
    authInfo match {
      case AuthInfo(account, Some(clientId), maybeScope, maybeRedirectUri) ⇒ (for {
        maybeClient <- oauthClientsDal.findByClientId(clientId)
        toAccessToken <- oauthAccessTokensDal.refresh(authInfo.user, maybeClient.get).map(toAccessToken) if maybeClient.isDefined
      } yield toAccessToken).recover { case _ ⇒ throw new InvalidClient() }

      case _ ⇒ Future.failed[AccessToken](new InvalidRequest())
    }

  }

  override def findAuthInfoByCode(code: String): Future[Option[AuthInfo[Account]]] = {
    oauthAuthorizationCodesDal.findByCode(code).flatMap {
      case Some(authAuthorizationCode) ⇒ getAuthInfo(
        accountId = authAuthorizationCode.accountId,
        clientId = authAuthorizationCode.oauthClientId
      )

      case None ⇒ Future.failed(new InvalidRequest())
    }
  }

  override def deleteAuthCode(code: String): Future[Unit] = {
    oauthAuthorizationCodesDal.delete(code).map(_ ⇒ {})
  }

  override def findAuthInfoByRefreshToken(refreshToken: String): Future[Option[AuthInfo[Account]]] = {
    oauthAccessTokensDal.findByRefreshToken(refreshToken, accessTokenExpireSeconds).flatMap {
      case Some(authAccessToken) ⇒ getAuthInfo(
        accountId = authAccessToken.accountId,
        clientId = authAccessToken.oauthClientId
      )

      case None ⇒ Future.failed(new InvalidRequest())
    }
  }

  override def findAuthInfoByAccessToken(accessToken: AccessToken): Future[Option[AuthInfo[Account]]] = {
    oauthAccessTokensDal.findByAccessToken(accessToken.token).flatMap {
      case Some(authAccessToken) ⇒ getAuthInfo(
        accountId = authAccessToken.accountId,
        clientId = authAccessToken.oauthClientId
      )

      case None ⇒ Future.failed(new InvalidRequest())
    }
  }

  override def findAccessToken(token: String): Future[Option[AccessToken]] = {
    oauthAccessTokensDal.findByAccessToken(token).map(_.map(toAccessToken))
  }

}
