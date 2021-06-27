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

import com.actionml.core.{HIO, engine}
import com.actionml.core.engine.{Action, Add, Delete, EnginesBackend, Update}
import com.actionml.core.store.backends.{MongoAsyncDao, MongoStorage}
import com.actionml.core.store.{DAO, DaoQuery}
import com.actionml.core.validate.{ValidRequestExecutionError, WrongParams}
import com.mongodb.CursorType
import com.mongodb.client.model.Filters
import com.typesafe.scalalogging.LazyLogging
import org.bson.codecs.configuration.CodecProvider
import org.mongodb.scala.model.CreateCollectionOptions
import org.mongodb.scala.{Document, Observer, SingleObservable}
import zio.{IO, ZIO}

import java.time.Instant
import scala.concurrent.Future


abstract class EnginesMongoBackend(codecs: List[CodecProvider]) extends EnginesBackend.Service with LazyLogging {
  import DaoQuery.syntax._
  private val storage = MongoStorage.getStorage("harness_meta_store", codecs = codecs)
  private lazy val enginesCollection = storage.createDao[EngineMetadata]("engines")
  private val engineEventsName = "engines_events"
  private val enginesEventsDao: DAO[Document] = zio.Runtime.default.unsafeRunSync {
    IO.fromFuture { implicit ec =>
      val opts = CreateCollectionOptions().capped(true).sizeInBytes(1000000L)
      import storage.db
      for {
        names <- db.listCollectionNames().toFuture()
        _ <- if (names.contains(engineEventsName)) Future.successful () // Assumes that collection was already created as capped
             else db.createCollection(engineEventsName, opts).toFuture
        _ <- db.getCollection(engineEventsName).countDocuments().flatMap {
          case 0L => db.getCollection(engineEventsName).insertOne(mkEvent("", "init")).map(_ => 1L)
          case _ => SingleObservable(-1L)
        }.toFuture
      } yield storage.createDao[Document](engineEventsName)
    }
  }.fold(c => throw c.failureOption.get, a => a)

  override def addEngine(id: String, data: EngineMetadata): HIO[Unit] = {
    enginesCollection.findOneById(id).fold {
      for {
        _ <- enginesCollection.insertIO(data)
        _ <- enginesEventsDao.insertIO(mkEvent(id, "add"))
      } yield ()
    } { _ =>
      ZIO.fail(WrongParams(s"Engine $id already exists, use update"))
    }
  }

  override def updateEngine(id: String, data: EngineMetadata): HIO[Unit] = {
    for {
      _ <- IO.effect(enginesCollection.saveOne("engineId" === id, data)).orElseFail(ValidRequestExecutionError())
      _ <- enginesEventsDao.insertIO(mkEvent(id, "update"))
    } yield ()
  }

  override def deleteEngine(id: String): HIO[Unit] = {
    for {
      _ <- IO.effect(enginesCollection.removeOne("engineId" === id)).unit.orElseFail(ValidRequestExecutionError())
      _ <- enginesEventsDao.insertIO(mkEvent(id, "delete"))
    } yield ()
  }

  override def watchActions(callback: Action => Unit): HIO[Unit] = {
    import DaoQuery.syntax._
    enginesCollection.findMany().foreach(e => callback(Add(e)))
    IO.effectAsync { cb =>
      enginesEventsDao.asInstanceOf[MongoAsyncDao[Document]]
        .collection
        .find(Filters.gt("timestamp", Instant.now))
        .cursorType(CursorType.TailableAwait)
        .noCursorTimeout(true)
        .subscribe(new Observer[Document] {
          override def onNext(doc: Document): Unit = {
            for {
              (engineId, action) <- getActionValue(doc)
            } yield action match {
              case "add" => enginesCollection.findOne("engineId" === engineId).foreach(e => callback(Add(e)))
              case "update" => enginesCollection.findOne("engineId" === engineId).foreach(e => callback(Update(e)))
              case "delete" => callback(Delete(EngineMetadata(engineId, "", "")))
              case "init" =>
              case _ => logger.warn("Unknown engine's action found")
            }
          }
          override def onError(e: Throwable): Unit = cb.apply {
            logger.error(s"$engineEventsName watch error", e)
            ZIO.fail(ValidRequestExecutionError(e.getMessage))
          }
          override def onComplete(): Unit = cb.apply {
            logger.error("Engines backend error - actions watch completed")
            ZIO.fail(ValidRequestExecutionError("Engines backend error"))
          }
        })
    }
  }

  override def listNodes: HIO[List[engine.NodeDescription]] = IO.succeed(Nil)


  private def mkEvent(engineId: String, eventName: String) = Document(
    "engineId" -> engineId,
    "action" -> eventName,
    "timestamp" -> new java.util.Date()
  )
  private def getActionValue(doc: Document): Option[(String, String)] = for {
    engineId <- doc.get("engineId")
    a <- doc.get("action")
    id <- util.Try(engineId.asString().getValue).toOption
    action <- util.Try(a.asString().getValue).toOption
  } yield id -> action
}
