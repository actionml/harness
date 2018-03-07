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

import com.actionml.core.dal.UsersDao
import com.actionml.core.model.User
import com.typesafe.scalalogging.LazyLogging
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.UpdateOptions

// for use with Scaldi
// class UsersDaoImpl(implicit inj: Injector) extends UsersDao with MongoSupport with Injectable with LazyLogging {
class UsersDaoImpl(dbName: String)(implicit inj: Injector) extends UsersDao with MongoSupport with Injectable with LazyLogging {

  // todo: must find a way to pass in a codec instead of a class
  // MongoSupport.registerCodec(classOf[User])
  private val name = dbName
  private val users = collection[User](name, "users")
  private implicit val ec = inject[ExecutionContext]

  override def findOne(_id: String): Future[Option[User]] = {
    users.find(equal("_id",_id))
      .toFuture
      .recover { case e =>
        logger.error(s"Can't find user with _id ${_id}", e)
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

  import org.mongodb.scala.model.Filters._
  import org.mongodb.scala.model.Updates._
  /** Pass in the modified User and it will be upserted */
  override def insertOrUpdateOne(user: User): Future[Unit] = {
    users.replaceOne(equal("_id", user._id), user, UpdateOptions().upsert(true))
      .toFuture
      .map(_ => ())
  }

  override def unsetProperties(userId: String, unsetPropKeys: Set[String]): Future[Unit] = {
    // there may be a way to do this better in Mongo code
    findOne(userId).map {
      case Some(User(_id, properties)) =>
        User(userId, properties -- unsetPropKeys)
      case _ => User("", Map.empty)
    }.flatMap(insertOrUpdateOne)

  }

  override def setProperties(userId: String, setProps: Map[String, Seq[String]]): Future[Unit] = {
    findOne(userId).map {
      case Some(User(_id, properties)) =>
        User(userId, properties ++ setProps)
      case None =>
        User(userId, setProps)
      case _ =>
        User("", Map.empty)
    }.flatMap(insertOrUpdateOne)
  }

  override def addPropValue(userId: String, propName: String, propValues: Seq[String], maxValues: Int = 10): Future[Unit] = {
    findOne(userId).map {
      case Some(User(_id, properties)) =>
        val existingValues = if (properties.keySet.contains(propName)) properties(propName) else Seq.empty[String]
        val newVals = (existingValues ++ propValues).takeRight(maxValues)
        User(userId, properties ++ Map(propName -> newVals))
      case None =>
        User(userId, Map(propName -> propValues))
      case _ =>
        User("", Map.empty)
    }.flatMap(insertOrUpdateOne)
  }

  override def insertOne(user: User): Future[Unit] = {
    users.insertOne(user)
      .toFuture
      .map(_ => ())
  }

  override def deleteOne(userId: String): Future[Unit] = {
    users.deleteOne(equal("_id", userId))
      .toFuture
      .map(result => if (result.getDeletedCount == 1) () else throw ObjectNotFoundException)
  }

}

