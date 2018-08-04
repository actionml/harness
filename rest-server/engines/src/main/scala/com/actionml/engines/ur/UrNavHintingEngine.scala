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

package com.actionml.engines.ur

import cats.data.Validated
import cats.data.Validated.Valid
import com.actionml.core.engine.{Engine, LambdaAlgorithm}
import com.actionml.core.model.GenericEvent
import com.actionml.core.validate.ValidateError
import com.actionml.engines.navhinting.{NHEvent, NavHintingAlgorithm, NavHintingEngine}

class UrNavHintingEngine extends NavHintingEngine {

  private var urAlgo: LambdaAlgorithm[NHEvent] = _

  override def initAndGet(json: String): UrNavHintingEngine = {
    if (super.init(json).isValid) {
      urAlgo = new URAlgorithm[NHEvent](json, this.dataset)
      this
    } else {
      logger.error(s"Parse error with Engine's JSON: $json")
      throw new RuntimeException("Parse error with Engine's JSON")
    }
  }

  override def train(): Validated[ValidateError, String] = {
    urAlgo.train()
  }
}

object UrNavHintingEngine {
  def apply(json: String): UrNavHintingEngine = {
    val engine = new UrNavHintingEngine
    engine.initAndGet(json)
  }
}
