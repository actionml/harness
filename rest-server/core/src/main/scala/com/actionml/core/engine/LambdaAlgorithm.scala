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
import com.actionml.core.validate.ValidateError
import com.typesafe.scalalogging.LazyLogging

/** Adds a method for train, which is expected to update the model through some potentially long lived task,
  *  usually in a batch or background mode.
  */
trait LambdaAlgorithm[T] extends LazyLogging {

  /** May be ignored by Lambda Algorithms if they do not update portions of the model in real-time.
    * @param datum The input Event type defined by the parmeterized type
    * @return
    */
  def input(datum: T): Validated[ValidateError, String] = Valid("")

  /** May train or queue up to execute the process method, such as when using a Spark compute engine
    * Override to use the queuing method to create the model
    * @return
    */
  def train(): Validated[ValidateError, String]

}

