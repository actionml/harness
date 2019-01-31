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

package com.actionml.core.backup

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}

import cats.data.Validated
import com.actionml.core.engine.Engine
import com.actionml.core.validate.ValidateError
import com.typesafe.scalalogging.LazyLogging

/**
  * Abstract class for JSON back up. Every json sent to POST /engines/engine-id/events will be mirrored by
  * a trait or object extending this.
  *
  */
abstract class Mirror(mirrorContainer: String, engineId: String) extends LazyLogging {

  def mirrorEvent(json: String): Validated[ValidateError, String]
  def importEvents(engine: Engine, location: String): Validated[ValidateError, String]

  /**
    * Collection names are formatted with "yy-MM-dd" engine. In a filesystems this is the file name
    * for mirrored files of events. It means differnent things so other types of Mirrors
    *
    * @return timestamp-based name
    */
  protected def batchName: String =
    // yearMonthDay is lexicographically sortable and one file per day seems like a good default.
    DateTimeFormatter.ofPattern("yy-MM-dd").format(LocalDateTime.now(ZoneId.of("UTC")))

  /**
    * Semantics of a Directory name, base dir/engine ID for all the implementations. Override if file/directory
    * semantics do not apply
    *
    * @param engineId Engine ID
    * @return directory name
    */
  protected def containerName: String = s"$mirrorContainer/$engineId"

}

/** Used when mirroring is config selectable */
object Mirror {
  val localfs = "localfs"
  val hdfs = "hdfs"
}
