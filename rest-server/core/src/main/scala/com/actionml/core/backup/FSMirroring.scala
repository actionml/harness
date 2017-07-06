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

import java.io.{File, FileWriter, PrintWriter}

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.template.Engine
import com.actionml.core.validate.{ValidRequestExecutionError, ValidateError}

import scala.io.Source

/**
  * Mirroring implementation for local FS.
  */

// TODO: not using injection so need a trait
//object FSMirroring extends Mirroring {
trait FSMirroring extends Mirroring {

  val f = new File(mirrorContainer)
  if (f.exists() && f.isDirectory && config.getString("mirror.type").nonEmpty) {
    logger.info(s"Morroring raw un-validated events to ${ mirrorContainer }")
  } else if (config.getString("mirror.type").nonEmpty) {
    logger.error(s"Mirror location: ${ mirrorContainer } not configured to accept event mirroring.")
  } else {
    logger.warn(s"Mirroring location is set but type is not configured, no mirroring with be done.")
  }


  // java.io.IOException could be thrown here in case of system errors
  override def mirrorEvent(engineId: String, json: String): Validated[ValidateError, Unit] = {
    def mirrorEventError(errMsg: String) =
      Invalid(ValidRequestExecutionError(s"Unable to mirror event: $errMsg"))

    if (mirrorType == Mirroring.localfs) {
      try {
        val resourceCollection = new File(containerName(engineId))
        //logger.info(s"${containerName(engineId)} exists: ${resourceCollection.exists()}")
        if (!resourceCollection.exists()) new File(s"${ containerName(engineId) }").mkdir()
        val pw = new PrintWriter(new FileWriter(s"${ containerName(engineId) }/$batchName.json", true))
        try {
          pw.write(json)
        } finally {
          pw.close()
        }
      } catch {
        case _: Exception =>
          val errMsg = "Problem mirroring while input"
          logger.error(errMsg)
          mirrorEventError(errMsg)
      }

    } else {
      val errMsg = "Local filesystem mirroring called, but not configured."
      logger.warn(errMsg)
      mirrorEventError(errMsg)
    }
    Valid(())
  }

  /** Read json event one per line as a single file or directory of files returning when done */
  override def importEvents(engine: Engine, location: String): Validated[ValidateError, Unit] = {
    def importEventsError(errMsg: String) = Invalid(ValidRequestExecutionError(
      s"""Unable to import from: $location on the servers file system to engineId: ${ engine.engineId }.
         | $errMsg""".stripMargin))
    try {
      val mirrorLocation = new File(containerName(engine.engineId))
      val resourceCollection = new File(location)
      if (mirrorLocation.getCanonicalPath.compare(resourceCollection.getCanonicalPath) != 0) {
        if (resourceCollection.exists() && resourceCollection.isDirectory) { // read all files as json and input
          val flist = new java.io.File(location).listFiles.filterNot(_.getName.contains(".")) // Spark json will be part-xxxxx files with no extension otherwise
          // .filter(_.getName.endsWith(".json"))
          logger.info(s"Reading files from directory: ${ location }")
          for (file <- flist) {
            Source.fromFile(file).getLines().foreach { line =>
              engine.input(line)
            }
          }
        } else if (resourceCollection.exists()) { // single file
          Source.fromFile(location).getLines().foreach { line =>
            engine.input(line)
          }
        }
      } else {
        val errMsg =
          s"""Cannot import from mirroring location: $location since imported files are also
             |mirrored causing an infinite loop. Copy or move them first.""".stripMargin
        logger.error(errMsg)
        importEventsError(errMsg)
      }
    } catch {
      case _: Exception =>
        val errMsg = "Problem while importing saved events"
        logger.error(errMsg)
        importEventsError(errMsg)
    }
    Valid(())
  }
}
