package com.actionml.core.backup

import java.io.{File, FileInputStream, IOException, _}

import com.typesafe.scalalogging.LazyLogging
import org.apache.hadoop.fs.Path
import zio.blocking.Blocking
import zio.stream.{ZStream, ZTransducer}
import zio.{IO, Task, ZManaged}

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
object Importer extends LazyLogging {

  def importEvents(location: String): Task[(Int, ZStream[Blocking, Throwable, String])] = {
    if (location.trim.startsWith("hdfs")) hdfsStream(location)
    else fsStream(location)
  }.map { case (filesRead, stream) =>
    filesRead -> stream.transduce(ZTransducer.utf8Decode).transduce(ZTransducer.splitLines).filter(_.trim.nonEmpty)
  }


  import collection.JavaConverters._
  private def fsStream(location: String): Task[(Int, ZStream[Blocking, IOException, Byte])] = {
    val io = IO.effect {
      val l = new File(location)
      if (l.isDirectory) {
        val files = l.listFiles().filterNot(_.getName.startsWith(".")).map(new FileInputStream(_)) // importing files that do not start with a dot like .DStore on Mac
        (files.length, new SequenceInputStream(asJavaEnumerationConverter(files.toIterator).asJavaEnumeration))
      } else (1, new FileInputStream(location))
    }
    io.map { case (i, _) => i -> ZStream.fromInputStreamManaged {
      ZManaged.make(io.map(_._2))(i => IO.effectTotal(i.close()))
        .mapError(e => new IOException(e))
    }}
  }

  private val hdfs = HDFSFactory.hdfs
  private def hdfsStream(location: String): Task[(Int, ZStream[Blocking, Throwable, Byte])] = {
    val filesStatuses = hdfs.listStatus(new Path(location))
    val filePaths = filesStatuses.map(_.getPath())
    val io = IO.effect(
      filePaths.size -> new SequenceInputStream(asJavaEnumerationConverter(filePaths.toIterator.map(f => hdfs.open(f).getWrappedStream)).asJavaEnumeration)
    )
    io.map { case (i, _) => i -> ZStream.fromInputStreamManaged {
      ZManaged.make(io.map(_._2))(i => IO.effectTotal(i.close()))
        .mapError(e => new IOException(e))
    }}
  }

}
