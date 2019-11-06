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
import java.util.concurrent.atomic.AtomicBoolean

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
  if (f.isDefined && f.get.exists() && f.get.isDirectory) logger.info(s"Engine-id: ${engineId}; Mirror raw un-validated events to $mirrorContainer")

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
  override def importEvents(engine: Engine, location: String, isActive: AtomicBoolean): Validated[ValidateError, String] = {
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
          logger.trace(s"Engine-id: ${engineId}. Reading files from directory: ${location}")
          var filesRead = 0
          var eventsProcessed = 0L
          for (file <- flist) {
            filesRead += 1
            logger.trace(s"Engine-id: ${engineId}. Importing from file: ${file.getName}")
            eventsProcessed = eventsProcessed + importFromFile(file, engine, isActive)
          }
          if(filesRead == 0 || eventsProcessed == 0)
            logger.warn(s"Engine-id: ${engineId}. No events were processed, did you mean to import JSON events from directory $location ?")
          else
            logger.trace(s"Engine-id: ${engineId}. Import read $filesRead files and processed $eventsProcessed events.")
        } else if (resourceCollection.exists()) { // single file
          val eventsProcessed = importFromFile(new File(location), engine, isActive)
          logger.info(s"Engine-id: ${engineId}. Import processed $eventsProcessed events.")
        }
      } else {
        val errMsg =
          s"""Engine-id: ${engineId}. Cannot import from mirroring location: $location since imported files are also
             |mirrored causing an infinite loop. Copy or move them first.""".stripMargin
        logger.error(errMsg)
        importEventsError(errMsg)
      }
    } catch {
      case e: IOException =>
        val errMsg = s"Engine-id: $engineId. Problem while importing saved events from $location, exception ${e.getMessage}"
        logger.error(errMsg, e)
        importEventsError(errMsg)
    } finally {
      logger.info(s"Engine-id: $engineId. Completed importing. Check logs for any data errors.")
    }
    Valid(jsonComment("Job created to import events in the background."))
  }

  private class CancelException extends RuntimeException
  private def importFromFile(file: File, engine: Engine, isActive: AtomicBoolean): Long = {
    var eventsProcessed = 0
    val src = Source.fromFile(file)
    try {
      src.getLines().sliding(512, 512).foreach { lines =>
        eventsProcessed += lines.size
        if (isActive.get)
          try {
            engine.inputMany(lines)
          } catch {
            case e: IOException =>
              logger.error(s"Engine-id: ${engine.engineId}. Found a bad event and is ignoring it: $lines exception ${e.getMessage}", e)
          }
        else throw new CancelException
      }
      eventsProcessed
    } catch {
      case e: IOException =>
        logger.error(s"Engine-id: ${engine.engineId}. Reading file: ${file.getName} exception ${e.getMessage}", e)
        eventsProcessed
      case _: CancelException =>
        logger.info(s"Import canceled on file ${file}. $eventsProcessed events processed")
        eventsProcessed
    } finally {
      src.close
    }
  }
}
