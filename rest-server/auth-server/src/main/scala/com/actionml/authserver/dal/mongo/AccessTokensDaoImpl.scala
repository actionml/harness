package com.actionml.authserver.dal.mongo

import com.actionml.authserver.dal.AccessTokensDao
import com.actionml.authserver.model.AccessToken
import org.bson.conversions.Bson
import org.mongodb.scala.model.Filters._
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}

class AccessTokensDaoImpl(implicit inj: Injector) extends AccessTokensDao with MongoSupport with Injectable {
  private implicit val executionContext = inject[ExecutionContext]
  private lazy val accessTokens = collection[AccessToken]("accessTokens")

  override def findByAccessToken(tokenString: String): Future[Option[AccessToken]] = {
    accessTokens
      .find(byToken(tokenString))
      .toFuture
      .recover { case e => e.printStackTrace(); List.empty}
      .map(_.headOption)
  }

  override def store(accessToken: AccessToken): Future[_] = {
    accessTokens.insertOne(accessToken).toFuture
  }

  override def remove(tokenString: String): Future[_] = {
    accessTokens.deleteOne(byToken(tokenString))
      .toFuture
  }


  private def byToken: String => Bson = s => equal("token", s)
}
