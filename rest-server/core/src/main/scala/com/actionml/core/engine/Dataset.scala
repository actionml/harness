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
import com.actionml.core.model.{Event, GenericEngineParams, User}
import com.actionml.core.store.Store
import com.actionml.core.validate.{JsonParser, ValidateError}
import com.typesafe.scalalogging.LazyLogging

import scala.reflect.ClassTag

abstract class Dataset[T](engineId: String) extends LazyLogging with JsonParser {

  // methods that must be implemented in the Engine
  def init(json: String, deepInit: Boolean = true): Validated[ValidateError, String]
  def destroy(): Unit
  def input(datum: String): Validated[ValidateError, Event]
  /** Required method to parserAndValidate the input event */
  def parseAndValidateInput(jsonEvent: String): Validated[ValidateError, T]

  // start and stop may be ignored by Engines if not applicable
  def start(): Dataset[T] = {logger.trace(s"Starting base Dataset"); this}
  def stop(): Unit = {logger.trace(s"Stopping base Dataset")}

}

abstract class SharedUserDataset[T](engineId: String, storage: Store) extends Dataset[T](engineId)
  with JsonParser with LazyLogging {

  val usersDAO = storage.createDao[User]("users")
  override def init(json: String, deepInit: Boolean = true): Validated[ValidateError, String] = {
    this.parseAndValidate[GenericEngineParams](json).andThen { p =>
      // todo: if we are updating, we should merge data for user from the engine dataset to the shared DB but there's no
      // way to detect that here since this class is newed in the Engine. deepInit will give a clue but still no way
      // to findOne old users that will be orphaned.
      // this should switch to using a shared user db if configs tells us to, but orphaned user data is left uncleaned
      Valid(jsonComment("Init processed"))
    }
  }

}
