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

import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}


trait DAO[T] extends AsyncDao[T] with SyncDao[T] {
  def name: String
}

trait AsyncDao[T] {
  def findAsync(filter: (String, Any)*)(implicit ec: ExecutionContext): Future[Option[T]]
  def findOneByIdAsync(id: String)(implicit ec: ExecutionContext): Future[Option[T]]
  def listAsync(query: DaoQuery = DaoQuery())(implicit ec: ExecutionContext): Future[Iterable[T]]
  def insertAsync(o: T)(implicit ec: ExecutionContext): Future[Unit]
  def updateAsync(filter: (String, Any)*)(o: T)(implicit ec: ExecutionContext): Future[T]
  def saveAsync(id: String, o: T)(implicit ec: ExecutionContext): Future[Unit]
  def saveAsync(o: T)(implicit ec: ExecutionContext): Future[Unit]
  def removeAsync(filter: (String, Any)*)(implicit ec: ExecutionContext): Future[T]
  def removeOneByIdAsync(id: String)(implicit ec: ExecutionContext): Future[T]
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
  def find(filter: (String, Any)*): Option[T] = sync(findAsync(filter: _*))
  def list(query: DaoQuery = DaoQuery()): Iterable[T] = sync(listAsync(query))
  def insert(o: T): Unit = sync(insertAsync(o))
  def update(filter: (String, Any)*)(o: T): T = sync(updateAsync(filter: _*)(o))
  def save(id: String, o: T): Unit = sync(saveAsync(id, o))
  def save(o: T): Unit = sync(saveAsync(o)) // save but create the primary key
  def remove(filter: (String, Any)*): T = sync(removeAsync(filter: _*))
  def removeOneById(id: String): T = sync(removeOneByIdAsync(id))
}
