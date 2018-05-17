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

package com.actionml.core.store.backends

import com.actionml.core.store.DAO
import com.typesafe.scalalogging.LazyLogging
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters
import org.scalacheck.Test.Failed

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.Success


class MongoDao[T](collection: MongoCollection[T])(implicit ct: ClassTag[T]) extends DAO[T] with LazyLogging {

  override def name = collection.namespace.getFullName

  override def findOneByIdAsync(id: String)(implicit ec: ExecutionContext): Future[Option[T]] = findAsync("_id" -> id)

  override def findAsync(filter: (String, Any)*)(implicit ec: ExecutionContext): Future[Option[T]] =
    collection.find(mkBson(filter)).headOption

  override def listAsync(filter: (String, Any)*)(implicit ec: ExecutionContext): Future[Iterable[T]] =
    collection.find(mkBson(filter)).toFuture

  override def insertAsync(o: T)(implicit ec: ExecutionContext): Future[Unit] = {
    collection.insertOne(o).headOption.flatMap {
      case Some(t) =>
        logger.debug(s"Successfully inserted $o into $name with result $t")
        Future.successful(t)
      case None =>
        logger.error(s"Can't insert value $o to collection ${collection.namespace}")
        Future.failed(new RuntimeException(s"Can't insert value $o to collection ${collection.namespace}"))
    }
  }

  override def updateAsync(filter: (String, Any)*)(o: T)(implicit ec: ExecutionContext): Future[T] =
    collection.findOneAndReplace(mkBson(filter), o).headOption.flatMap {
      case Some(t) =>
        logger.debug(s"Successfully updated object $o with the filter $filter")
        Future.successful(t)
      case None =>
        logger.error(s"Can't update collection ${collection.namespace} with filter $filter and value $o")
        Future.failed(new RuntimeException(s"Can't update collection ${collection.namespace} with filter $filter and value $o"))
    }

  override def saveAsync(id: String, o: T)(implicit ec: ExecutionContext): Future[Unit] = {
    val filter = mkBson(Seq("_id" -> id))
    (for {
      opt <- collection.find(filter).headOption
      _ <- if (opt.isDefined) collection.replaceOne(filter, o).headOption.recover {
             case e => logger.error(s"Can't replace object $o", e)
           } else insertAsync(o)
    } yield ()).map(_ => logger.debug(s"Object $o with id $id (filter: $filter) saved successfully into $name"))
      .recover { case e => logger.error(s"Can't save object $o with id $id (filter $filter) into $name", e)}
  }

  override def removeAsync(filter: (String, Any)*)(implicit ec: ExecutionContext): Future[T] =
    collection.findOneAndDelete(mkBson(filter)).headOption.flatMap {
      case Some(t) =>
        logger.debug(s"$filter was successfully removed from collection $collection with namespace ${collection.namespace}. Result: $t")
        Future.successful(t)
      case None =>
        logger.error(s"Can't remove from collection ${collection.namespace} with filter $filter")
        Future.failed(new RuntimeException(s"Can't remove from collection ${collection.namespace} with filter $filter"))
    }

  override def removeOneByIdAsync(id: String)(implicit ec: ExecutionContext): Future[T] = removeAsync("_id" -> id)


  private def mkBson(fields: Seq[(String, Any)]): Bson = {
    if (fields.isEmpty) Document("_id" -> Document("$exists" -> true))
    else Filters.and(fields.map { case (k, v) => Filters.eq(k, v) }.toArray: _*)
  }
}
