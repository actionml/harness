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

package com.actionml.core.storage

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag


trait Storage {
  def createDao[T](collectionName: String)(implicit ct: ClassTag[T]): DAO[T]
  def removeCollection(name: String)(implicit ec: ExecutionContext): Future[Unit]
  def drop()(implicit ec: ExecutionContext): Future[Unit]
}

trait DAO[T] {
  def find(filter: (String, Any)*)(implicit ec: ExecutionContext): Future[Option[T]]
  def list(filter: (String, Any)*)(implicit ec: ExecutionContext): Future[Iterable[T]]
  def insert(o: T)(implicit ec: ExecutionContext): Future[Unit]
  def update(filter: (String, Any)*)(o: T)(implicit ec: ExecutionContext): Future[T]
  def upsert(filter: (String, Any)*)(o: T)(implicit ec: ExecutionContext): Future[Unit]
  def remove(filter: (String, Any)*)(implicit ec: ExecutionContext): Future[T]
}
