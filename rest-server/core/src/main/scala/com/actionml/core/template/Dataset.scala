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

package com.actionml.core.template

import cats.data.Validated
import com.actionml.core.storage.Store
import com.actionml.core.validate.ValidateError
import com.typesafe.scalalogging.LazyLogging

abstract class Dataset[T](engineId: String) extends LazyLogging {

  val resourceId: String = engineId

  def init(json: String): Validated[ValidateError, Boolean]
  def destroy(): Unit
  def start() = {logger.trace(s"Starting base Dataset"); this}
  def stop(): Unit = {logger.trace(s"Stopping base Dataset")}

  def input(datum: String): Validated[ValidateError, Event]

  def parseAndValidateInput(s: String): Validated[ValidateError, T]

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
