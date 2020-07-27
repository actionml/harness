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

package com.actionml.admin

import akka.actor.ActorSystem
import com.actionml.core.HIO
import com.actionml.core.config.EtcdConfig
import com.actionml.core.engine.backend.EnginesEtcdBackend
import com.actionml.core.validate.JsonSupport

class EtcdAdministrator private(_config: EtcdConfig, override val system: ActorSystem) extends EnginesEtcdBackend[EngineMetadata] with Administrator with JsonSupport {

  override protected def encode: EngineMetadata => String = toJsonString
  override protected def decode: String => HIO[EngineMetadata] = parseAndValidateIO[EngineMetadata](_)

  override protected def config: EtcdConfig = _config
}

object EtcdAdministrator {
  def apply(config: EtcdConfig, system: ActorSystem) = new EtcdAdministrator(config, system)
}
