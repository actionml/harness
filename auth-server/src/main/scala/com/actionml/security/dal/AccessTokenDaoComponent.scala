package com.actionml.security.dal

import com.actionml.security.model.AccessToken

import scala.concurrent.Future

trait AccessTokenDaoComponent {

  trait AccessTokenDao {
    def findByAccessToken(token: String): Future[AccessToken]
  }

  def accessTokenDao: AccessTokenDao
}
