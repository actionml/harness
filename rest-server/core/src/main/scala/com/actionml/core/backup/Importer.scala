package com.actionml.core.backup

import com.actionml.core.engine.Engine

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

/** Imports JSON events through the input method of the engine, works with any of several filesystems. HDFS, local FS
  * and in the future perhaps others like Kafka
  */
object Importer {

  def importEvents(engine: Engine, location: String): Unit = {
    // determine which filesystem is specified
    //todo: finish this refactor to decouple import from mirror, import from anywhere, mirror as the engine config says
  }

}
