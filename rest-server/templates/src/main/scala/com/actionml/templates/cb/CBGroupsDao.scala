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

import com.actionml.core.model.CBGroup

import scala.concurrent.Future


trait CBGroupsDao {
  def findOne(id: String): Future[Option[CBGroup]]
  def list(offset: Int, limit: Int): Future[Iterable[CBGroup]]
  def insertOrUpdateOne(user: CBGroup): Future[Unit]
  def insertOne(user: CBGroup): Future[Unit]
  def deleteOne(userId: String): Future[Unit]
}
