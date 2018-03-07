/*
 * Copyright ActionML, LLC under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * ActionML licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
