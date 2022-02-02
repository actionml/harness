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
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.HIO
import com.actionml.core.model.{Comment, GenericEngineParams, GenericEvent, Response}
import com.actionml.core.engine.Dataset
import com.actionml.core.jobs.JobDescription
import com.actionml.core.validate._

import scala.concurrent.Future
import scala.language.reflectiveCalls

/** Scaffold for a Dataset, does nothing but is a good starting point for creating a new Engine
  * Extend with the store of choice, like Mongo or other Store trait.
  * This is not the minimal Template because many methods are implemented generically in the
  * base classes but is better used as a starting point for new Engines.
  *
  * @param engineId The Engine ID
  */
class ScaffoldDataset(engineId: String) extends Dataset[GenericEvent](engineId) with JsonSupport {

  // These should only be called from trusted source like the CLI!
  override def init(json: String, update: Boolean = false): Validated[ValidateError, Response] = {
    parseAndValidate[GenericEngineParams](json).andThen { p =>
      // Do something with parameters--do not re-initialize the algo if update == false
      Valid(p)
    }
    Valid(Comment("ScaffoldDataset initialized"))
  }

  /** Cleanup all persistent data or processes created by the Dataset */
  override def destroy() = {
  }

  // Parse, validate, drill into the different derivative event types, andThen(persist)?
  override def input(json: String): Validated[ValidateError, GenericEvent] = {
    // good place to persist in whatever way the specific event type requires
    parseAndValidate[GenericEvent](json).andThen { event =>
      event.event match {
        case _ => // Based on attributes in the event one can parseAndVaidate more specific types here
          logger.trace(s"Dataset: ${engineId} parsing a usage event: ${event.event}")
          parseAndValidate[GenericEvent](json)
      }
    }.andThen(Valid(_)) // may persist or otherwise react to the parsed and validated event
  }

  override def inputAsync(datum: String): Validated[ValidateError, Future[Response]] = Invalid(NotImplemented())

  override def getUserData(userId: String, num: Int, from: Int): Validated[ValidateError, List[Response]] = throw new NotImplementedError()

  override def deleteUserData(userId: String): HIO[JobDescription] = throw new NotImplementedError()
}

