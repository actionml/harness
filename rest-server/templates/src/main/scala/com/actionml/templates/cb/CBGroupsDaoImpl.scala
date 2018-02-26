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

package com.actionml.templates.cb

import com.actionml.core.dal.mongo.{MongoSupport, ObjectNotFoundException}
import com.actionml.core.model.CBGroup
import com.typesafe.scalalogging.LazyLogging
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.UpdateOptions
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}

// for use with Scaldi
// class groupsDaoImpl(implicit inj: Injector) extends groupsDao with MongoSupport with Injectable with LazyLogging {
class CBGroupsDaoImpl(dbName: String)(implicit inj: Injector) extends CBGroupsDao with MongoSupport with Injectable with LazyLogging {

  // todo: must find a way to pass in a codec instead of a class
  // MongoSupport.registerCodec(classOf[group])
  private val name = dbName
  private val groups = collection[CBGroup](name, "groups")
  private implicit val ec = inject[ExecutionContext]

  override def findOne(_id: String): Future[Option[CBGroup]] = {
    groups.find(equal("_id",_id))
      .toFuture
      .recover { case e =>
        logger.error(s"Can't find group with _id ${_id}", e)
        List.empty
      }.map(_.headOption)
  }

  override def list(offset: Int, limit: Int): Future[Iterable[CBGroup]] = {
    groups.find()
      .skip(offset)
      .limit(limit)
      .toFuture
      .recover {
        case e: Exception =>
          logger.error(s"Can't list groups: ${e.getMessage}", e)
          List.empty
      }
  }

  import org.mongodb.scala.model.Filters._
  /** Pass in the modified CBGroup and it will be upserted */
  override def insertOrUpdateOne(group: CBGroup): Future[Unit] = {
    groups.replaceOne(equal("_id", group._id), group, UpdateOptions().upsert(true))
      .toFuture
      .map(_ => ())
  }

  override def insertOne(group: CBGroup): Future[Unit] = {
    groups.insertOne(group)
      .toFuture
      .map(_ => ())
  }

  override def deleteOne(groupId: String): Future[Unit] = {
    groups.deleteOne(equal("_id", groupId))
      .toFuture
      .map(result => if (result.getDeletedCount == 1) () else throw ObjectNotFoundException)
  }

}

