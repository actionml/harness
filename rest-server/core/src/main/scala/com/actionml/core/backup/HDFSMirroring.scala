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
import java.net.URI

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.engine.Engine
import com.actionml.core.validate.{ValidRequestExecutionError, ValidateError}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FSDataInputStream, FileSystem, Path}

/**
  * Mirroring implementation for HDFS.
  */

class HDFSMirroring(mirrorContainer: String, engineId: String)
  extends Mirroring(mirrorContainer, engineId) {

  private val hdfs = HDFSFactories.hdfs

  private val rootMirrorDir = if(hdfs.exists(new Path("/mirrors"))) {
    val engineEventMirrorPath = new Path(mirrorContainer, engineId)
    if(!hdfs.exists(engineEventMirrorPath)) {
      try {
        hdfs.mkdirs(new Path(mirrorContainer, engineId))
        Some(engineEventMirrorPath)
      } catch {
        case ex: IOException =>
          logger.error(s"Unable to create the new mirror location ${new Path(mirrorContainer, engineId).getName}")
          logger.error("This error is non-fatal but means events will not be mirrored", ex)
          None
        case unknownException =>
          logger.error(s"Unable to create the new mirror location ${new Path(mirrorContainer, engineId).getName}", unknownException)
          throw unknownException
      }
    } else if(hdfs.isDirectory(engineEventMirrorPath)) Some(engineEventMirrorPath) else None
  } else None // None == no mirroring allowed



  // java.io.IOException could be thrown here in case of system errors
  override def mirrorEvent(json: String): Validated[ValidateError, Boolean] = {
    // Todo: this should be rewritten for the case where mirroring is only used for import
    def mirrorEventError(errMsg: String) =
      Invalid(ValidRequestExecutionError(s"Unable to mirror event to HDFS: $errMsg"))

    if(rootMirrorDir.isDefined) {
      try {
        val batchFilePath = new Path(rootMirrorDir.get, batchName)
        val eventsFile = if(hdfs.exists(batchFilePath)) {
          hdfs.append(batchFilePath)
        } else {
          hdfs.create(batchFilePath)
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
        Valid(true)
      } catch {
        case ex: IOException =>
          val errMsg = "Problem mirroring input to HDFS"
          logger.error(errMsg, ex)
          mirrorEventError(s"$errMsg: ${ex.getMessage}")
      }

    } else mirrorEventError("Problem mirroring input to HDFS. No valid mirror location.")
  }

  // todo: should read in a thread and return at once after setup check
  /** Read json event one per line as a single file or directory of files returning when done */
  override def importEvents(engine: Engine, location: String): Validated[ValidateError, Boolean] = {
    def importEventsError(errMsg: String) = Invalid(ValidRequestExecutionError(
      s"""Unable to import from: $location on the servers file system to engineId: ${engine.engineId}.
         | $errMsg""".stripMargin))

    if(rootMirrorDir.isDefined && location == rootMirrorDir.get.toString) {
      logger.error("Reading from the mirror location will cause in infinite loop." +
        "\nTry moving the files to a new location before doing a batch import.")
      importEventsError("Reading from the mirror location will cause in infinite loop." +
        "\nTry moving the files to a new location before doing a batch import.")
    } else if (!location.isEmpty) {
      try {
        val filesStatuses = hdfs.listStatus(new Path(location))
        val filePaths = filesStatuses.map(_.getPath())
        logger.info(s"Number of file in dir: ${filePaths.size} values: ${filePaths.toString}")
        for (filePath <- filePaths) {
          val file = hdfs.open(filePath)
          val lineReader = new BufferedReader(new InputStreamReader(file))
          var line = lineReader.readLine()
          while (line != null) {
            // logger.info(s"Event from HDFS file ${filePath.getName}\n$line")
            engine.input(line)
            line = lineReader.readLine()
          }
        }
        Valid(true)
      } catch {
        case ex: IOException =>
          val errMsg = "Problem reading input from HDFS"
          logger.error(errMsg, ex)
          importEventsError(s"$errMsg: ${ex.getMessage}")
      }
    } else importEventsError("No location to read import files from")
  }

}

object HDFSFactories {

  private lazy val hdfsConfDir = sys.env.getOrElse("HDFS_CONF_DIR", "/usr/local/hadoop/etc/hadoop")

  lazy val conf: Configuration = {
    val config = new Configuration()
    config.setBoolean("dfs.support.append", true) // used by mirroring
    // the following are the minimum needed from the hdfs setup files even if hdfs is not setup
    // on this machine these must be copied from the namenode/master
    config.addResource(new Path(s"$hdfsConfDir/core-site.xml"))
    config.addResource(new Path(s"$hdfsConfDir/hdfs-site.xml"))
    config
  }

  lazy val hdfs: FileSystem = FileSystem.get(conf)
}
