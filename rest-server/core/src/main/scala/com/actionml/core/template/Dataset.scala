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

abstract class Dataset[T](r: String) extends LazyLogging {

  val resourceId: String = r
  val store: Store

  def create(): Dataset[T]
  def destroy(): Dataset[T]
  def persist(datum: T): Validated[ValidateError, Event]
  // todo: we may want to use some operators overloading if this fits some Scala idiom well
  // takes one json, possibly an Event, returns HTTP Status code 200 or 400 (failure to parse or validate)
  def input(datum: String): Validated[ValidateError, Event]

  def parseAndValidateInput(s: String): Validated[ValidateError, T]

}

trait Event
