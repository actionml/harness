package com.actionml.oauth2.dal

import com.actionml.oauth2.entities.{Account, OAuthAccessToken, OAuthClient}

import scala.concurrent.Future

/**
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  */
trait OAuthAccessTokensDal{
  def create(account: Account, client: OAuthClient): Future[OAuthAccessToken]
  def delete(account: Account, client: OAuthClient): Future[Int]
  def refresh(account: Account, client: OAuthClient): Future[OAuthAccessToken]
  def findByAccessToken(accessToken: String): Future[Option[OAuthAccessToken]]
  def findByAuthorized(account: Account, clientId: String): Future[Option[OAuthAccessToken]]
  def findByRefreshToken(refreshToken: String, accessTokenExpireSeconds: Int): Future[Option[OAuthAccessToken]]
}
