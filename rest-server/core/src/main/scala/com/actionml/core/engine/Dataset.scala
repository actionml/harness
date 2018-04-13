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

package com.actionml.core.engine

import cats.data.Validated
import cats.data.Validated.Valid
import com.actionml.core.model.{GenericEngineParams, User}
import com.actionml.core.store.Store
import com.actionml.core.validate.{JsonParser, ValidateError}
import com.typesafe.scalalogging.LazyLogging

abstract class Dataset[T] extends LazyLogging {

  def init(json: String, deepInit: Boolean = true): Validated[ValidateError, Boolean]
  def destroy(): Unit
  def start() = {logger.trace(s"Starting base Dataset"); this}
  def stop(): Unit = {logger.trace(s"Stopping base Dataset")}

  def input(datum: String): Validated[ValidateError, Event]

  def parseAndValidateInput(s: String): Validated[ValidateError, T]

}

abstract class SharedUserDataset[T](storage: Store) extends Dataset[T]
  with JsonParser with LazyLogging {

  val usersDAO = storage.createDao[User]("users")
  override def init(json: String, deepInit: Boolean = true): Validated[ValidateError, Boolean] = {
    this.parseAndValidate[GenericEngineParams](json).andThen { p =>
      // todo: if we are updating, we should merge data for user from the engine dataset to the shared DB but there's no
      // way to detect that here since this class is newed in the Engine. deepInit will give a clue but still no way
      // to find old users that will be orphaned.

      // this should switch to using a shared user db if configs tells us to, but orphaned user data is left uncleaned
//      object UsersDAO extends SalatDAO[User, String](collection = connection(p.sharedDBName.getOrElse(resourceId))("users"))
      Valid(true)
    }
  }

}
// allows us to look at what kind of specialized event to create
case class GenericEvent (
  //eventId: String, // not used in Harness, but allowed for PIO compatibility
  event: String,
  entityType: String,
  entityId: String,
  targetEntityId: Option[String] = None,
  properties: Option[Map[String, Any]] = None,
  eventTime: String, // ISO8601 date
  creationTime: String) // ISO8601 date
  extends Event

trait Event
