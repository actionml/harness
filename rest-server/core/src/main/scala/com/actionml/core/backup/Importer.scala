package com.actionml.core.backup

import java.io.{IOException, SequenceInputStream}
import java.net.URI

import com.typesafe.scalalogging.LazyLogging
import org.apache.hadoop.fs.{FileSystem, Path}
import zio.blocking.Blocking
import zio.stream.{ZStream, ZTransducer}
import zio.{IO, Managed, Task, UIO, ZManaged}

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
    def cleanUp: FileSystem => UIO[Unit] = fs => IO.effect(fs.close()).mapError { e =>
      logger.error(s"File system error [URI=$location]", e)
    }.ignore

    val uri = if (!location.contains("://")) s"file://$location" else location
    Managed.make(IO.effect(HDFSFactory.newInstance(new URI(uri))))(cleanUp).use { fs =>
      val filesStatuses = fs.listStatus(new Path(uri))
      val filePaths = filesStatuses.map(_.getPath())
      val io = IO.effect {
        import collection.JavaConverters._
        filePaths.length -> new SequenceInputStream(
          asJavaEnumerationConverter(filePaths.toIterator.map(f => fs.open(f))).asJavaEnumeration
        )
      }
      io.map { case (i, _) => i -> ZStream.fromInputStreamManaged {
        ZManaged.make(io.map(_._2))(i => IO.effectTotal(i.close()))
          .mapError(e => new IOException(e))
      }}
    }
  }.map { case (filesRead, stream) =>
    filesRead -> stream.transduce(ZTransducer.utf8Decode).transduce(ZTransducer.splitLines).filter(_.trim.nonEmpty)
  }
}
