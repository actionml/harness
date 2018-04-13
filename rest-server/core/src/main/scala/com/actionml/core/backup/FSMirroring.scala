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
import com.actionml.core.engine.Engine
import com.actionml.core.validate.{ValidRequestExecutionError, ValidateError}

import scala.io.Source

/**
  * Mirroring implementation for local FS.
  */

class FSMirroring(mirrorContainer: String) extends Mirroring(mirrorContainer) {

  private val f = if(mirrorContainer.isEmpty) None else Some(new File(mirrorContainer))
  if (f.isDefined && f.get.exists() && f.get.isDirectory) logger.info(s"Mirroring raw un-validated events to $mirrorContainer")

  // java.io.IOException could be thrown here in case of system errors
  override def mirrorEvent(engineId: String, json: String): Validated[ValidateError, Boolean] = {
    // Todo: this should be rewritten for the case where mirroring is only used for import
    def mirrorEventError(errMsg: String) =
      Invalid(ValidRequestExecutionError(s"Unable to mirror event: $errMsg"))

      if (mirrorContainer != ""){
        try {
          val resourceCollection = new File(containerName(engineId))
          //logger.info(s"${containerName(engineId)} exists: ${resourceCollection.exists()}")
          if (!resourceCollection.exists()) new File(s"${ containerName(engineId) }").mkdir()
          val fn = batchName
          val pw = new PrintWriter(new FileWriter(s"${ containerName(engineId) }/$batchName.json", true))
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

    Valid(true)
  }

  /** Read json event one per line as a single file or directory of files returning when done */
  override def importEvents(engine: Engine, location: String): Validated[ValidateError, Boolean] = {
    def importEventsError(errMsg: String) = Invalid(ValidRequestExecutionError(
      s"""Unable to import from: $location on the servers file system to engineId: ${ engine.engineId }.
         | $errMsg""".stripMargin))
    try {
      val mirrorLocation = new File(containerName(engine.engineId))
      val resourceCollection = new File(location)
      if (mirrorLocation.getCanonicalPath.compare(resourceCollection.getCanonicalPath) != 0) {
        if (resourceCollection.exists() && resourceCollection.isDirectory) { // read all files as json and input
          // val flist = new java.io.File(location).listFiles.filterNot(_.getName.contains(".")) // Spark json will be
          // part-xxxxx files with no extension otherwise use .filter(_.getName.endsWith(".json"))
          // not mirrored with Spark, but may import from some source that creates a spark-like directory of part files
          // Todo: update for Spark
          val flist = new java.io.File(location).listFiles.filterNot(_.getName.startsWith(".")) // importing files that do not start with a dot like .DStore on Mac
          logger.info(s"Reading files from directory: ${location}")
          for (file <- flist) {
            Source.fromFile(file).getLines().foreach { line =>
              engine.input(line)
            }
          }
        } else if (resourceCollection.exists()) { // single file
          logger.info(s"Reading events from single file: ${location}")
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
    Valid(true)
  }
}
