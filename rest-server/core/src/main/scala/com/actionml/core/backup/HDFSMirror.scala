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
import org.apache.hadoop.fs.Path
import zio._

import scala.language.implicitConversions
import scala.util.control.NonFatal
import scala.util.{Success, Try}


class HDFSMirror(override val mirrorContainer: String, override val engineId: String) extends Mirror with JsonSupport {
  private lazy val fs: org.apache.hadoop.fs.FileSystem = HDFSFactory.hdfs
  private val rootMirrorDir = Try {
    val engineEventMirrorPath = new Path(mirrorContainer, engineId)
    if (!fs.exists(engineEventMirrorPath)) {
      try {
        fs.mkdirs(new Path(mirrorContainer, engineId))
        engineEventMirrorPath
      } catch {
        case ex: IOException =>
          logger.error(s"Engine-id: ${engineId}. Unable to create the new mirror location ${engineEventMirrorPath.getName}")
          logger.error(s"Engine-id: ${engineId}. This error is non-fatal but means events will not be mirrored", ex)
          throw ex
        case NonFatal(unknownException) =>
          logger.error(s"Engine-id: ${engineId}. Unable to create the new mirror location ${engineEventMirrorPath.getName}", unknownException)
          throw unknownException
      }
    } else if (fs.isDirectory(engineEventMirrorPath)) {
      engineEventMirrorPath
    } else {
      val ex = new RuntimeException("Mirror location is not a directory")
      logger.error(s"Engine-id: $engineId. Unable to create the new mirror location ${engineEventMirrorPath.getName}", ex)
      throw ex
    }
  }

  val Success((eventsFile, writer)) = Try {
    val batchFilePath = new Path(rootMirrorDir.get, batchName)
    val eventsFile = if (fs.exists(batchFilePath)) {
      fs.append(batchFilePath)
    } else {
      fs.create(batchFilePath)
    }
    // following pattern from:
    // https://blog.knoldus.com/simple-java-program-to-append-to-a-file-in-hdfs/
    (eventsFile, new PrintWriter(eventsFile))
  }


  override def mirrorEvent(json: String): Task[Unit] = IO.effect {
    writer.append(json)
    writer.flush()
    eventsFile.hflush()
    //eventsFile.writeUTF(json) // this seems to prepend lines with 0x01 and other chars, UTF?
  }.onError {
    case ex =>
      val errMsg = s"Engine-id: ${engineId}. Problem mirroring input to HDFS"
      logger.error(errMsg, ex)
      IO.unit
  }

  override val cleanup: Task[Unit] = {
    implicit def catchAllErrors: Unit => UIO[Unit] = a => IO.effect(a).catchAll { e =>
      logger.error("Cleanup error", e)
      IO.unit
    }
    for {
      _ <- writer.close()
      _ <- eventsFile.close()
      _ <- fs.close()
    } yield ()
  }
}

