package com.actionml.authserver.dal

import com.actionml.authserver.model.AccessToken

import scala.concurrent.Future

trait AccessTokenDaoComponent {

  trait AccessTokenDao {
    def findByAccessToken(token: String): Future[AccessToken]
  }

  def accessTokenDao: AccessTokenDao
}
