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
import com.actionml.core.validate.{JsonSupport, ParseError, ValidRequestExecutionError}
import com.typesafe.scalalogging.LazyLogging
import zio.blocking.{effectBlocking, effectBlockingInterrupt}
import zio.duration._
import zio.{IO, Queue, Schedule, Task, ZIO, ZManaged}

import java.io._
import java.nio.charset.StandardCharsets
import scala.util.Try

/**
  * Mirror implementation for local FS.
  */
class FSMirror(override val mirrorContainer: String, override val engineId: String) extends Mirror with JsonSupport {

  private var putEventToQueue: Option[String => Task[Unit]] = None

  private def init(): HIO[Unit] = if (mirrorContainer.nonEmpty) {
    for {
      q <- Queue.unbounded[String]
      _ = putEventToQueue = Some(s => q.offer(s).unit)
      container <- containerName
      dir = new File(container)
      _ = if (!dir.exists()) dir.mkdirs()
      _ = logger.info(s"Engine-id: ${engineId}; Mirror raw un-validated events to $container")
      path = s"$container${File.separator}$batchName.json"
      out = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(path, true)), StandardCharsets.UTF_8)
      _ <- IO.effect(out.flush()).repeat(Schedule.linear(2.seconds)).forkDaemon
      _ <- q.take.map(out.append(_)).forever.forkDaemon
    } yield ()
  } else IO.unit

  override def mirrorEvent(event: String): Task[Unit] =
    putEventToQueue.fold[Task[Unit]](IO.unit) { _.apply(event)
      .catchAll(e => IO.effect(logger.error("Problem mirroring while input", e)))
    }

  override def cleanup(): Task[Unit] = IO.unit
}

object FSMirror extends LazyLogging {
  def create(mirrorContainer: String, engineId: String): HIO[FSMirror] = {
    for {
      m <- IO.effect(new FSMirror(mirrorContainer, engineId))
        .mapError { e =>
          logger.error(s"Engine create error $engineId", e)
          ParseError(s"Error creating engine $engineId with such configuration")
        }
      _ <- m.init()
    } yield m
  }
}