package com.actionml.authserver.dal.mongo

import com.actionml.authserver.dal.AccessTokenDao
import com.actionml.authserver.model.AccessToken
import com.mongodb.client.model.Filters
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}

class AccessTokenDaoImpl(implicit inj: Injector) extends AccessTokenDao with MongoSupport with AccessTokenConverters with Injectable {
  private implicit val executionContext = inject[ExecutionContext]

  override def findByAccessToken(token: String): Future[AccessToken] = {
    mongoDb.getCollection("accessTokens")
      .find(Filters.eq("accessToken" -> token))
      .head()
      .map(toAccessToken)
  }
}
