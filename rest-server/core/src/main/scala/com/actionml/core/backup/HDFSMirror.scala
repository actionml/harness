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

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.validate.{JsonSupport, ValidRequestExecutionError, ValidateError}
import org.apache.hadoop.fs.Path


class HDFSMirror(mirrorContainer: String, engineId: String)
  extends Mirror(mirrorContainer, engineId) with JsonSupport {

  private lazy val fs: org.apache.hadoop.fs.FileSystem = ???

  private val rootMirrorDir = if (fs.exists(new Path("/mirrors"))) {
    val engineEventMirrorPath = new Path(mirrorContainer, engineId)
    if (!fs.exists(engineEventMirrorPath)) {
      try {
        fs.mkdirs(new Path(mirrorContainer, engineId))
        Some(engineEventMirrorPath)
      } catch {
        case ex: IOException =>
          logger.error(s"Engine-id: ${engineId}. Unable to create the new mirror location ${new Path(mirrorContainer, engineId).getName}")
          logger.error(s"Engine-id: ${engineId}. This error is non-fatal but means events will not be mirrored", ex)
          None
        case unknownException: Exception =>
          logger.error(s"Engine-id: ${engineId}. Unable to create the new mirror location ${new Path(mirrorContainer, engineId).getName}", unknownException)
          throw unknownException
      }
    } else Some(engineEventMirrorPath).filter(fs.isDirectory)
  } else None // None == no mirroring allowed



  // java.io.IOException could be thrown here in case of system errors
  override def mirrorEvent(json: String): Validated[ValidateError, String] = {
    // Todo: this should be rewritten for the case where mirroring is only used for import
    def mirrorEventError(errMsg: String) =
      Invalid(ValidRequestExecutionError(jsonComment(s"Unable to mirror event to HDFS: $errMsg")))

    if(rootMirrorDir.isDefined) {
      try {
        val batchFilePath = new Path(rootMirrorDir.get, batchName)
        val eventsFile = if(fs.exists(batchFilePath)) {
          fs.append(batchFilePath)
        } else {
          fs.create(batchFilePath)
        }
        // following pattern from:
        // https://blog.knoldus.com/simple-java-program-to-append-to-a-file-in-hdfs/
        val writer = new PrintWriter(eventsFile)
        writer.append(json)
        writer.flush()
        eventsFile.hflush()
        writer.close()
        eventsFile.close()
        //eventsFile.writeUTF(json) // this seems to prepend lines with 0x01 and other chars, UTF?
        //eventsFile.hflush()
        //eventsFile.close()
        Valid(jsonComment("Event mirrored"))
      } catch {
        case ex: IOException =>
          val errMsg = s"Engine-id: ${engineId}. Problem mirroring input to HDFS"
          logger.error(errMsg, ex)
          mirrorEventError(s"$errMsg: ${ex.getMessage}")
      }

    } else mirrorEventError("Problem mirroring input to HDFS. No valid mirror location.")
  }
}

