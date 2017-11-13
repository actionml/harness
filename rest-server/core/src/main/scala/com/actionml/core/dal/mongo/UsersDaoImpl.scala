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

package com.actionml.core.dal.mongo

import com.typesafe.scalalogging.LazyLogging
import com.actionml.core.dal.UsersDao
import com.actionml.core.model.User
import model.dal.mongo.MongoSupport
import scaldi.{Injectable, Injector}
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag


class UsersDaoImpl(implicit inj: Injector) extends UsersDao with MongoSupport with Injectable with LazyLogging {

  // todo: must find a way to pass in a codec instead of a class
  // MongoSupport.registerCodec(classOf[User])
  private val users = collection[User](None, "users")

  private implicit val ec = inject[ExecutionContext]

  override def find(id: String): Future[Option[User]] = {
    users.find(equal("id", id))
      .toFuture
      .recover { case e =>
        logger.error(s"Can't find user with id $id", e)
        List.empty
      }.map(_.headOption)
  }

  override def find(id: String, secretHash: String): Future[Option[User]] = {
    users.find(and(
      equal("id", id),
      equal("secretHash", secretHash)
    )).toFuture
      .recover { case e =>
        logger.error(s"Can't find user with id $id and password hash $secretHash", e)
        List.empty
      }.map(_.headOption)
  }

  override def list(offset: Int, limit: Int): Future[Iterable[User]] = {
    users.find()
      .skip(offset)
      .limit(limit)
      .toFuture
      .recover {
        case e: Exception =>
          logger.error(s"Can't list users: ${e.getMessage}", e)
          List.empty
      }
  }

  override def update(user: User): Future[Unit] = {
    users.insertOne(user)
      .toFuture
      .map(_ => ())
  }

  def delete(userId: String): Future[Unit] = {
    users.deleteOne(equal("id", userId))
      .toFuture
      .map(result => if (result.getDeletedCount == 1) () else throw UserNotFoundException)
  }

}

trait NotFoundException extends RuntimeException

case object UserNotFoundException extends RuntimeException  ("User not found") with NotFoundException
