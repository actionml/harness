package com.actionml.authserver.dal.mongo

import com.actionml.authserver.dal.AccessTokensDao
import com.actionml.authserver.model.AccessToken
import com.mongodb.client.model.Filters
import org.mongodb.scala.Document
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}

class AccessTokensDaoImpl(implicit inj: Injector) extends AccessTokensDao with MongoSupport with AccessTokenConverters with Injectable {
  private implicit val executionContext = inject[ExecutionContext]

  override def findByAccessToken(token: String): Future[AccessToken] = {
    collection[Document]("accessTokens")
      .find(Filters.eq("accessToken" -> token))
      .head
      .map(toAccessToken)
  }
}
