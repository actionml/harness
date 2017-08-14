package com.actionml.authserver.dal.mongo

import com.actionml.core.ExecutionContextComponent
import com.actionml.authserver.dal.AccessTokenDaoComponent
import com.actionml.authserver.model.AccessToken
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
