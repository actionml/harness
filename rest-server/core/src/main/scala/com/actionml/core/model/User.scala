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

package com.actionml.core.model

case class User(
    _id: String = "",
    properties: Map[String, Seq[String]] = Map.empty) {
  /*
  def propsToMapOfSeq = properties.map { case(propId, propString) =>
    propId -> propString.split("%").toSeq
  }
  */
}


object User { // convert the Map[String, Seq[String]] to Map[String, String] by encoding the propery values in a single string

  /*
  def propsToMapString(props: Map[String, Seq[String]]): Map[String, String] = {
    props.filter { (t) =>
      t._2.size != 0 && t._2.head != ""
    }.map { case (propId, propSeq) =>
      propId -> propSeq.mkString("%")
    }
  }
  */
}

