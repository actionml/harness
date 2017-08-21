package com.actionml.authserver.dal.mongo

import com.actionml.authserver.dal.AccessTokensDao
import com.actionml.authserver.model.AccessToken
import org.mongodb.scala.model.Filters._
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}

class AccessTokensDaoImpl(implicit inj: Injector) extends AccessTokensDao with MongoSupport with Injectable {
  private implicit val executionContext = inject[ExecutionContext]
  private val accessTokens = collection[AccessToken]("accessTokens")

  override def findByAccessToken(token: String): Future[Option[AccessToken]] = {
    accessTokens
      .find(equal("token", token))
      .toFuture
      .map(_.headOption)
  }

  override def store(accessToken: AccessToken): Future[_] = {
    accessTokens.insertOne(accessToken).toFuture
  }
}
