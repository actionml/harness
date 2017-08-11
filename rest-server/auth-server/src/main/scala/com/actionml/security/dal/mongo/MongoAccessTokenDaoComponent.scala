package com.actionml.security.dal.mongo

import com.actionml.core.ExecutionContextComponent
import com.actionml.security.dal.AccessTokenDaoComponent
import com.actionml.security.model.AccessToken
import com.mongodb.client.model.Filters

import scala.concurrent.Future

trait MongoAccessTokenDaoComponent extends AccessTokenDaoComponent {
  this: ExecutionContextComponent =>

  override def accessTokenDao: AccessTokenDao = new AccessTokenDaoImpl

  class AccessTokenDaoImpl extends AccessTokenDao with MongoSupport with AccessTokenConverters {
    override def findByAccessToken(token: String): Future[AccessToken] = {
      mongoDb.getCollection("accessTokens")
        .find(Filters.eq("accessToken" -> token))
        .head()
        .map(toAccessToken)
    }
  }

}
