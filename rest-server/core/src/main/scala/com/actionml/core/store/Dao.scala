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

import java.util.concurrent.Executors

import com.actionml.core.store.DaoQuery.QueryCondition
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal


trait DAO[T] extends AsyncDao[T] {
  def name: String
  def dbName: String
  def collectionName: String
  def findOneById(id: String): Option[T]
  def findOne(filter: (String, QueryCondition)*): Option[T]
  def findMany(query: DaoQuery = DaoQuery()): Iterable[T]
  def findMany(filter: (String, QueryCondition)*): Iterable[T]
  def insert(o: T): Unit
  def insertMany(c: Seq[T]): Unit
  def update(filter: (String, QueryCondition)*)(o: T): T
  def saveOneById(id: String, o: T): Unit
  def saveOne(filter: (String, QueryCondition), o: T): Unit
  def removeOneById(id: String): T
  def removeOne(filter: (String, QueryCondition)*): T
  def removeMany(filter: (String, QueryCondition)*): Unit
  def createIndexes(ttl: Duration = 365.days): Unit
}

trait AsyncDao[T] {
  def findOneAsync(filter: (String, QueryCondition)*)(implicit ec: ExecutionContext): Future[Option[T]]
  def findOneByIdAsync(id: String): Future[Option[T]]
  def findManyAsync(query: DaoQuery = DaoQuery())(implicit ec: ExecutionContext): Future[Iterable[T]]
  def insertAsync(o: T)(implicit ec: ExecutionContext): Future[Unit]
  def insertManyAsync(c: Seq[T])(implicit ec: ExecutionContext): Future[Unit]
  def updateAsync(filter: (String, QueryCondition)*)(o: T)(implicit ec: ExecutionContext): Future[T]
  def saveOneByIdAsync(id: String, o: T)(implicit ec: ExecutionContext): Future[Unit]
  def saveOneAsync(filter: (String, QueryCondition), o: T)(implicit ec: ExecutionContext): Future[Unit]
  def removeOneByIdAsync(id: String)(implicit ec: ExecutionContext): Future[T]
  def removeOneAsync(filter: (String, QueryCondition)*)(implicit ec: ExecutionContext): Future[T]
  def removeManyAsync(filter: (String, QueryCondition)*)(implicit ec: ExecutionContext): Future[Unit]
  def createIndexesAsync(ttl: Duration): Future[Unit]
}

trait SyncDao[T] extends DAO[T] with LazyLogging { self: AsyncDao[T] =>
  protected implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(32))
  private val timeout = 5.seconds
  private def sync[A](f: => Future[A]): A = try {
    Await.result(f, timeout)
  } catch {
    case NonFatal(e) =>
      logger.error("Sync DAO error", e)
      throw e
  }

  override def findOneById(id: String): Option[T] = sync(findOneByIdAsync(id))
  override def findOne(filter: (String, QueryCondition)*): Option[T] = sync(findOneAsync(filter: _*))
  override def findMany(query: DaoQuery = DaoQuery()): Iterable[T] = sync(findManyAsync(query))
  override def findMany(filter: (String, QueryCondition)*): Iterable[T] = sync(findManyAsync(DaoQuery(filter = filter)))
  override def insert(o: T): Unit = sync(insertAsync(o))
  override def insertMany(c: Seq[T]): Unit = sync(insertManyAsync(c))
  override def update(filter: (String, QueryCondition)*)(o: T): T = sync(updateAsync(filter: _*)(o))
  override def saveOneById(id: String, o: T): Unit = sync(saveOneByIdAsync(id, o))
  // saveOne will overwrite an object if the primary key already exists, like a Mongo upsert
  override def saveOne(filter: (String, QueryCondition), o: T): Unit = sync(saveOneAsync(filter, o)) // saveOneById but create the primary key
  override def removeOneById(id: String): T = sync(removeOneByIdAsync(id))
  override def removeOne(filter: (String, QueryCondition)*): T = sync(removeOneAsync(filter: _*))
  override def removeMany(filter: (String, QueryCondition)*): Unit = sync(removeManyAsync(filter: _*))
  override def createIndexes(ttl: Duration): Unit = sync(createIndexesAsync(ttl))
}
