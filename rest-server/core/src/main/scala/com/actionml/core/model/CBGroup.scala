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

import org.joda.time.DateTime

case class CBGroup (
    _id: String,
    testPeriodStart: DateTime, // ISO8601 date
    pageVariants: Map[String, String], //((1 -> "17"),(2 -> "18"))
    testPeriodEnd: Option[DateTime])
  extends CBEvent {

  def keysToInt(v: Map[String, String]): Map[Int, String] = {
    v.map( a => a._1.toInt -> a._2)
  }
}

trait CBEvent extends Event

