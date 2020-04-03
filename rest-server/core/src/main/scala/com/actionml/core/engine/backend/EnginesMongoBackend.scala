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

package com.actionml.core.engine.backend

import com.actionml.core.store.backends.{MongoAsyncDao, MongoStorage}
import com.actionml.core.store.{DAO, DaoQuery}
import com.actionml.core.validate.{ValidRequestExecutionError, ValidateError}
import com.mongodb.CursorType
import com.typesafe.scalalogging.LazyLogging
import org.bson.codecs.configuration.CodecProvider
import org.mongodb.scala.model.CreateCollectionOptions
import org.mongodb.scala.{Document, Observer}
import zio.{IO, Task, ZLayer}

import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import scala.util.control.NonFatal


abstract class EnginesMongoBackend[A: TypeTag: ClassTag] extends EnginesBackend[String, A, Document] with LazyLogging {
  import DaoQuery.syntax._
  private val storage = MongoStorage.getStorage("harness_meta_store", codecs = codecs)
  private lazy val enginesCollection = storage.createDao[A]("engines")
  private val engineEventsName = "engines_events"
  private val rt = zio.Runtime.unsafeFromLayer(ZLayer.succeed())
  private val enginesEventsDao: DAO[Document] = rt.unsafeRunSync {
    IO.fromFuture { implicit ec =>
      val opts = CreateCollectionOptions().capped(true).sizeInBytes(9000000000L)
      import storage.db
      for {
        names <- db.listCollectionNames().toFuture()
        _ <- if (names.contains(engineEventsName)) Future.successful () // Assumes that collection was already created as capped
             else db.createCollection(engineEventsName, opts).toFuture
      } yield storage.createDao[Document](engineEventsName)
    }
  }.fold(c => throw c.failureOption.get, a => a)

  private def logAndIgnore: Task[_] => IO[ValidateError, Unit] = _.catchAll { e =>
    IO.effectTotal(logger.error("Engines events error", e))
  }.map(a => logger.info(s"Task completed with result $a"))

  override def addEngine(id: String, data: A): IO[ValidateError, Unit] = {
    for {
      _ <- enginesCollection.insertIO(data)
      _ <- enginesEventsDao.insertIO(mkEvent(id, "add"))
    } yield ()
  }

  override def updateEngine(id: String, data: A): IO[ValidateError, Unit] = {
    IO.effect(enginesCollection.saveOne("engineId" === id, data))
      .mapError(_ => ValidRequestExecutionError())
  }

  override def deleteEngine(id: String): IO[ValidateError, Unit] = {
    for {
      _ <- IO.effect(enginesCollection.removeOne("engineId" === id)).unit
        .mapError(_ => ValidRequestExecutionError())
      _ <- enginesEventsDao.insertIO(mkEvent(id, "delete"))
    } yield ()
  }

  override def findEngine(id: String): IO[ValidateError, A] = {
    IO.effect(enginesCollection.findOne("engineId" === id).get)
      .mapError(_ => ValidRequestExecutionError())
  }

  override def listEngines: IO[ValidateError, Iterable[A]] = {
    IO.effect(enginesCollection.findMany())
      .mapError(_ => ValidRequestExecutionError())
  }

  override def onChange(callback: () => Unit): Unit = {
    startWatching(callback)
  }

  private def startWatching(callback: () => Unit): Unit = {
    enginesEventsDao.asInstanceOf[MongoAsyncDao[Document]]
      .collection
      .find()
      .cursorType(CursorType.TailableAwait)
      .noCursorTimeout(true)
      .subscribe(new Observer[Document] {
        override def onNext(result: Document): Unit = {
          try {
            callback()
          } catch {
            case NonFatal(e) => logger.error("Engines events watch error", e)
          }
        }
        override def onError(e: Throwable): Unit = {
          logger.error(s"$engineEventsName watch error", e)
          startWatching(callback)
        }
        override def onComplete(): Unit = {}
      })
  }

  def codecs: List[CodecProvider]


  private def mkEvent(id: String, eventName: String) = Document(
    "id" -> id,
    "action" -> eventName,
    "timestamp" -> new java.util.Date()
  )
}
