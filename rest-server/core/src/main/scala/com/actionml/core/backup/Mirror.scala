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

import com.actionml.core.HIO
import com.actionml.core.config.AppConfig

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}
import org.apache.hadoop.fs.Path
import zio.{RIO, Task, UIO}

import java.io.File
import scala.util.Try

/**
  * Trait for JSON back up. Every json sent to POST /engines/engine-id/events will be mirrored by
  * a trait or object extending this.
  */
trait Mirror {
  val engineId: String
  sys.addShutdownHook(zio.Runtime.default.unsafeRunSync(cleanup()))

  /**
    * Collection names are formatted with "yy-MM-dd" engine. In a filesystems this is the file name
    * for mirrored files of events. Used differently for other types of Mirrors
    *
    * @return timestamp-based name
    */
  protected val batchName: String =
    // yearMonthDay is lexicographically sortable and one file per day seems like a good default.
    DateTimeFormatter.ofPattern("yy-MM-dd").format(LocalDateTime.now(ZoneId.of("UTC")))

  protected val mirrorContainer: String

  /**
    * Semantics of a Directory name, base dir/engine ID for all the implementations. Override if file/directory
    * semantics do not apply
    *
    * @return directory name
    */
  val containerName: HIO[String] = AppConfig.hostName.map { hostname =>
    s"$mirrorContainer${File.separator}$hostname${File.separator}$engineId"
  }


  def mirrorEvent(event: String): Task[Unit]
  def cleanup(): Task[Unit]
}

object MirrorTypes extends Enumeration {
  type MirrorType = Value

  val localfs: MirrorType = Value("localfs")
  val hdfs: MirrorType = Value("hdfs")

  def withNameOpt(s: String): Option[MirrorType] = Try(withName(s)).toOption
}