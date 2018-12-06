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

package com.actionml.core.store

import com.actionml.core.store.DaoQuery.QueryCondition
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}


trait DAO[T] extends AsyncDao[T] with SyncDao[T] {
  def name: String
  def dbName: String
  def collectionName: String
}

trait AsyncDao[T] {
  def findOneAsync(filter: (String, QueryCondition)*)(implicit ec: ExecutionContext): Future[Option[T]]
  def findOneByIdAsync(id: String)(implicit ec: ExecutionContext): Future[Option[T]]
  def findManyAsync(query: DaoQuery = DaoQuery())(implicit ec: ExecutionContext): Future[Iterable[T]]
  def insertAsync(o: T)(implicit ec: ExecutionContext): Future[Unit]
  def insertManyAsync(c: Seq[T])(implicit ec: ExecutionContext): Future[Unit]
  def updateAsync(filter: (String, QueryCondition)*)(o: T)(implicit ec: ExecutionContext): Future[T]
  def saveOneByIdAsync(id: String, o: T)(implicit ec: ExecutionContext): Future[Unit]
  def saveOneAsync(o: T)(implicit ec: ExecutionContext): Future[Unit]
  def removeOneByIdAsync(id: String)(implicit ec: ExecutionContext): Future[T]
  def removeOneAsync(filter: (String, QueryCondition)*)(implicit ec: ExecutionContext): Future[T]
  def removeManyAsync(filter: (String, QueryCondition)*)(implicit ec: ExecutionContext): Future[Unit]
}

trait SyncDao[T] extends LazyLogging { self: AsyncDao[T] =>
  import scala.concurrent.ExecutionContext.Implicits.global
  private val timeout = 5 seconds
  private def sync[A](f: => Future[A]): A = try {
    Await.result(f, timeout)
  } catch {
    case e: Throwable =>
      logger.error("Sync DAO error", e)
      throw e
  }

  def findOneById(id: String): Option[T] = sync(findOneByIdAsync(id))
  def findOne(filter: (String, QueryCondition)*): Option[T] = sync(findOneAsync(filter: _*))
  def findMany(query: DaoQuery = DaoQuery()): Iterable[T] = sync(findManyAsync(query))
  def insert(o: T): Unit = sync(insertAsync(o))
  def insertMany(c: Seq[T]): Unit = sync(insertManyAsync(c))
  def update(filter: (String, QueryCondition)*)(o: T): T = sync(updateAsync(filter: _*)(o))
  def saveOneById(id: String, o: T): Unit = sync(saveOneByIdAsync(id, o))
  // saveOne will overwrite an object if the primary key already exists, like a Mongo upsert
  def saveOne(o: T): Unit = sync(saveOneAsync(o)) // saveOneById but create the primary key
  def removeOneById(id: String): T = sync(removeOneByIdAsync(id))
  def removeOne(filter: (String, QueryCondition)*): T = sync(removeOneAsync(filter: _*))
  def removeMany(filter: (String, QueryCondition)*): Unit = sync(removeManyAsync(filter: _*))
}
