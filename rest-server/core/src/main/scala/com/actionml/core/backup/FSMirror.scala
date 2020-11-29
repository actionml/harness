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

import java.io._

import com.actionml.core.validate.JsonSupport
import zio.{IO, Task}

import scala.util.Try

/**
  * Mirror implementation for local FS.
  */
class FSMirror(mirrorContainer: String, override val engineId: String) extends Mirror with JsonSupport {

  private val out = Try {
    val f = new File(containerName)
    if (!f.exists()) f.mkdirs()
    logger.info(s"Engine-id: ${engineId}; Mirror raw un-validated events to $mirrorContainer")
    new BufferedWriter(new PrintWriter(s"$containerName/$batchName.json", "UTF-8"))
  }

  override def mirrorEvent(event: String): Task[Unit] = {
    for {
      o <- IO.fromTry(out)
      _ <- IO.effect(o.write(event))
    } yield ()
  }.onError { c =>
    c.failures.foreach(e => logger.error("Problem mirroring while input", e))
    IO.unit
  }

  override def cleanup(): Task[Unit] =
    for {
      o <- IO.fromTry(out)
    } yield o.close
}
