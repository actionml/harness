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

import com.actionml.core.storage.{Mongo, Store}
import com.typesafe.scalalogging.LazyLogging

abstract class Dataset[T](r: String) extends LazyLogging {

  val resourceId = r
  val store: Store

  def create(): Dataset[T]
  def destroy(): Dataset[T]
  def persist(datum: T): Int
  // todo: we may want to use some operators overloading if this fits some Scala idiom well
  // takes one json, possibly an Event, returns HTTP Status code 200 or 400 (failure to parse or validate)
  def input(datum: String): Int

  def parseAndValidateInput(s: String): (T, Int)

}
