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

package com.actionml.templates.cb

import com.actionml.core.storage.Mongo
import com.actionml.core.template.{AlgorithmParams, Algorithm}

class CBAlgorithm(p: CBAlgoParams) extends Algorithm( new Mongo, p: CBAlgoParams ) {


}

case class CBAlgoParams(
    dataset: CBDataset, // required, where to get data
    maxIter: Int = 100, // the rest of these are VW params
    regParam: Double = 0.0,
    stepSize: Double = 0.1,
    bitPrecision: Int = 24,
    modelName: String = "model.vw",
    namespace: String = "n",
    maxClasses: Int = 3)
  extends AlgorithmParams
