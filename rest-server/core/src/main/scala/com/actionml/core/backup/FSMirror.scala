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

import java.io.{File, FileWriter, IOException, PrintWriter}

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.engine.Engine
import com.actionml.core.validate.{JsonSupport, ValidRequestExecutionError, ValidateError}

import scala.io.Source

/**
  * Mirror implementation for local FS.
  */

class FSMirror(mirrorContainer: String, engineId: String)
  extends Mirror(mirrorContainer, engineId) with JsonSupport {

  private val f = if(mirrorContainer.isEmpty) None else Some(new File(mirrorContainer))
  if (f.isDefined && f.get.exists() && f.get.isDirectory) logger.info(s"Mirror raw un-validated events to $mirrorContainer")

  // java.io.IOException could be thrown here in case of system errors
  override def mirrorEvent(json: String): Validated[ValidateError, String] = {
    // Todo: this should be rewritten for the case where mirroring is only used for import
    def mirrorEventError(errMsg: String) =
      Invalid(ValidRequestExecutionError(jsonComment(s"Unable to mirror event: $errMsg")))

    if (mirrorContainer != ""){
      try {
        val resourceCollection = new File(containerName)
        //logger.info(s"${containerName(engineId)} exists: ${resourceCollection.exists()}")
        if (!resourceCollection.exists()) new File(s"$containerName").mkdir()
        val fn = batchName
        val pw = new PrintWriter(new FileWriter(s"$containerName/$batchName.json", true))
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

  /** Read json event one per line as a single file or directory of files returning when done */
  override def importEvents(engine: Engine, location: String): Validated[ValidateError, String] = {
    def importEventsError(errMsg: String) = Invalid(ValidRequestExecutionError(
      jsonComment(s"""Unable to import from: $location on the servers file system to engineId: ${ engine.engineId }.
         | $errMsg""".stripMargin)))
    try {
      val mirrorLocation = new File(containerName)
      val resourceCollection = new File(location)
      if (mirrorLocation.getCanonicalPath.compare(resourceCollection.getCanonicalPath) != 0) {
        if (resourceCollection.exists() && resourceCollection.isDirectory) { // read all files as json and input
          // val flist = new java.io.File(location).listFiles.filterNot(_.getName.contains(".")) // Spark json will be
          // part-xxxxx files with no extension otherwise use .filter(_.getName.endsWith(".json"))
          // not mirrored with Spark, but may import from some source that creates a spark-like directory of part files
          // Todo: update for Spark
          val flist = new java.io.File(location).listFiles.filterNot(_.getName.startsWith(".")) // importing files that do not start with a dot like .DStore on Mac
          logger.info(s"Reading files from directory: ${location}")
          var filesRead = 0
          var eventsProcessed = 0
          for (file <- flist) {
            filesRead += 1
            logger.info(s"Importing from file: ${file.getName}")
            try {
              Source.fromFile(file).getLines().foreach { line =>
                eventsProcessed += 1
                try {
                  engine.input(line)
                } catch {
                  case e: IOException =>
                    logger.error(s"Bad event being ignored: $line exception ${e.printStackTrace()}")
                }
              }
            } catch {
            case e: IOException =>
              logger.error(s"Error reading file: ${file.getName} exception ${e.printStackTrace()}")
            }
          }
          if(filesRead == 0 || eventsProcessed == 0)
            logger.warn(s"No events were processed, did you mean to import JSON events from directory $location ?")
          else
            logger.info(s"Import read $filesRead files and processed $eventsProcessed events.")
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
      case e: IOException =>
        val errMsg = s"Problem while importing saved events from $location, exception ${e.printStackTrace()}"
        logger.error(errMsg)
        importEventsError(errMsg)
    } finally {
      logger.info("Completed importing. Check logs for any data errors.")
    }
    Valid(jsonComment("Job created to import events in the background."))
  }
}
