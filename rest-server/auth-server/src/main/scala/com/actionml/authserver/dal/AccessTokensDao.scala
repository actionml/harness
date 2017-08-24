package com.actionml.authserver.dal

import com.actionml.authserver.model.AccessToken

import scala.concurrent.Future

trait AccessTokensDao {
  def findByAccessToken(token: String): Future[Option[AccessToken]]
  def store(accessToken: AccessToken): Future[_]
  def remove(accessToken: String): Future[_]
}
