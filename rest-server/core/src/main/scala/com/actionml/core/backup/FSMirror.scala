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

/**
  * Mirror implementation for local FS.
  */

class FSMirror(mirrorContainer: String, engineId: String)
  extends Mirror(mirrorContainer, engineId) with JsonSupport {

  private val f = if(mirrorContainer.isEmpty) None else Some(new File(mirrorContainer))
  if (f.isDefined && f.get.exists() && f.get.isDirectory) logger.info(s"Engine-id: ${engineId}; Mirror raw un-validated events to $mirrorContainer")

  // java.io.IOException could be thrown here in case of system errors
  override def mirrorEvent(json: String): Validated[ValidateError, String] = {
    // Todo: this should be rewritten for the case where mirroring is only used for import
    // todo: is this best implemented in a non-blocking way for the engine.inputAsync case?
    def mirrorEventError(errMsg: String) =
      Invalid(ValidRequestExecutionError(jsonComment(s"Unable to mirror event: $errMsg")))

    if (Option(mirrorContainer).map(_.trim).exists(_.nonEmpty)) {
      try {
        val resourceCollection = new File(containerName)
        //logger.info(s"${containerName(engineId)} exists: ${resourceCollection.exists()}")
        if (!resourceCollection.exists()) resourceCollection.mkdirs()
        val fn = batchName
        // pat: old method used PrintWriter, new method uses BufferedWriter
        // val pw = new PrintWriter(new FileWriter(s"$containerName/$batchName.json", true))
        val pw = new BufferedWriter(new FileWriter(s"$containerName/$batchName.json", true))
        try {
          pw.write(json)
        } finally {
          pw.close()
        }
      } catch {
        case ex: Exception =>
          val errMsg = "Problem mirroring while input"
          logger.error(errMsg, ex)
          mirrorEventError(s"$errMsg: ${ex.getMessage}")
      }
    }
    Valid(jsonComment("Event mirrored"))
  }
}
