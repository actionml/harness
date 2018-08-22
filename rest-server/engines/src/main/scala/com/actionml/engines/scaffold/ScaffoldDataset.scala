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

package com.actionml.engines.scaffold

import cats.data.Validated
import cats.data.Validated.Valid
import com.actionml.core.model.{GenericEngineParams, GenericEvent}
import com.actionml.core.engine.{Dataset}
import com.actionml.core.validate._

import scala.language.reflectiveCalls

/** Scaffold for a Dataset, does nothing but is a good starting point for creating a new Engine
  * Extend with the store of choice, like Mongo or other Store trait.
  * This is not the minimal Template because many methods are implemented generically in the
  * base classes but is better used as a starting point for new Engines.
  *
  * @param engineId The Engine ID
  */
class ScaffoldDataset(engineId: String) extends Dataset[GenericEvent](engineId) with JsonParser {

  // These should only be called from trusted source like the CLI!
  override def init(json: String, deepInit: Boolean = true): Validated[ValidateError, Boolean] = {
    parseAndValidate[GenericEngineParams](json).andThen { p =>
      // Do something with parameters--do not re-initialize the algo if deepInit == false
      Valid(p)
    }
    Valid(true)
  }

  /** Cleanup all persistent data or processes created by the Dataset */
  override def destroy() = {
  }

  // Parse, validate, drill into the different derivative event types, andThen(persist)?
  override def input(json: String): Validated[ValidateError, GenericEvent] = {
    // good place to persist in whatever way the specific event type requires
    parseAndValidateInput(json).andThen(Valid(_))
  }

  /** Required method to parserAndValidate the input event */
  override def parseAndValidateInput(json: String): Validated[ValidateError, GenericEvent] = {
    parseAndValidate[GenericEvent](json).andThen { event =>
      event.event match {
        case _ => // Based on attributes in the event one can parseAndVaidate more specific types here
          logger.trace(s"Dataset: ${engineId} parsing a usage event: ${event.event}")
          parseAndValidate[GenericEvent](json)
      }
    }
  }

}

