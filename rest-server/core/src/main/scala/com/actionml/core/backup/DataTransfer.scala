package com.actionml.core.backup

import java.io.{BufferedWriter, File, FileWriter, IOException}

import com.actionml.core.engine.Engine
import com.actionml.core.validate.JsonSupport
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

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

/** Imports JSON events through the input method of the engine, works with any of several filesystems. HDFS, local FS
  * and in the future perhaps others like Kafka
  */
trait DataTransfer {
  def importEvents(location: String)(implicit ec: ExecutionContext): Future[Unit]
  def exportEvents(location: String)(implicit ec: ExecutionContext): Future[Unit]
}

object FSDataTransfer extends DataTransfer with LazyLogging with JsonSupport {
  this: Engine =>

  override def importEvents(location: String)(implicit ec: ExecutionContext): Future[Unit] = {
    Future {
      val resourceCollection = new File(location)
      if (resourceCollection.exists() && resourceCollection.isDirectory) {
        val flist = new java.io.File(location).listFiles.filterNot(_.getName.startsWith(".")) // importing files that do not start with a dot like .DStore on Mac
        logger.trace(s"Engine-id: ${this.engineId}. Reading files from directory: ${location}")
        var filesRead = 0
        var eventsProcessed = 0L
        for (file <- flist) {
          filesRead += 1
          logger.trace(s"Engine-id: ${this.engineId}. Importing from file: ${file.getName}")
          eventsProcessed = eventsProcessed + importFromFile(file)
        }
        if(filesRead == 0 || eventsProcessed == 0)
          logger.warn(s"Engine-id: ${this.engineId}. No events were processed, did you mean to import JSON events from directory $location ?")
        else
          logger.trace(s"Engine-id: ${this.engineId}. Import read $filesRead files and processed $eventsProcessed events.")
      } else if (resourceCollection.exists()) { // single file
        val eventsProcessed = importFromFile(new File(location))
        logger.info(s"Engine-id: ${this.engineId}. Import processed $eventsProcessed events.")
      }
    }.recover {
      case e: IOException =>
        val errMsg = s"Engine-id: $this.engineId. Problem while importing saved events from $location, exception ${e.getMessage}"
        logger.error(errMsg, e)
    }.map { _ =>
      logger.info(s"Engine-id: $this.engineId. Completed importing. Check logs for any data errors.")
    }
  }


  private def importFromFile(file: File): Long = {
    var eventsProcessed = 0
    // todo: this impl is very ugly tangling up mirror writing with importing
    try {
      val src = Source.fromFile(file)
      src.getLines().sliding(512, 512).foreach { lines =>
        eventsProcessed += lines.size
        try {
          this.inputMany(lines)
        } catch {
          case e: IOException =>
            logger.error(s"Engine-id: ${this.engineId}. Found a bad event and is ignoring it: $lines exception ${e.getMessage}", e)
        }
      }
      src.close()
      eventsProcessed
    } catch {
      case e: IOException =>
        logger.error(s"Engine-id: ${this.engineId}. " +
          s"Importing from file: ${file.getName} No mirror location. \n" +
          s"exception ${e.getMessage}", e)
        eventsProcessed
    }
  }

  override def exportEvents(location: String)(implicit ec: ExecutionContext): Future[Unit] = ???
}
