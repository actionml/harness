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

package com.actionml.templates.navhinting

import com.actionml.core.template.Model

/** In-memory list of nav ids addressable by id (fast) with weights passed in from Algorithm so not much to do here. */
class NavHintingModel() extends Model {

  def getMatchesSorted(eligible: Seq[String], vector: Map[String, Double]): Seq[String] = {
    val vectorIds = vector.keySet
    eligible.filter { id =>
      vectorIds.contains(id)
    }.map { id =>
      (id, vector(id))
    }.sortWith(_._2 > _._2).map(_._1)
  }

}
