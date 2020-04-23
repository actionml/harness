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

package com.actionml.core.engine.backend

import com.actionml.core.validate.ValidateError
import zio.{IO, Queue}

/*
 * type parameters:
 *   I - type of engine id
 *   D - application level type of engine's metadata
 *   S - storage level type of engine's metadata
 */
trait EnginesBackend[I,D,S] {
  def addEngine(id: I, data: D): IO[ValidateError, Unit]
  def updateEngine(id: I, data: D): IO[ValidateError, Unit]
  def deleteEngine(id: I): IO[ValidateError, Unit]
  def findEngine(id: I): IO[ValidateError, D]
  def listEngines: IO[ValidateError, Iterable[D]]
  def changesQueue: IO[ValidateError, Queue[Unit]]
}