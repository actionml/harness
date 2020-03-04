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

import com.actionml.core.store.DaoQuery
import com.actionml.core.store.backends.{MongoAsyncDao, MongoStorage}
import com.actionml.core.validate.{ValidRequestExecutionError, ValidateError}
import org.bson.codecs.configuration.CodecProvider
import org.mongodb.scala.Document
import zio.IO

import scala.reflect.runtime.universe._
import scala.reflect.ClassTag


abstract class EnginesMongoBackend[A: TypeTag: ClassTag] extends EnginesBackend[String, A, Document] {
  import DaoQuery.syntax._
  private val storage = MongoStorage.getStorage("harness_meta_store", codecs = codecs)
  private lazy val enginesCollection = storage.createDao[A]("engines")
  private var callback: () => Unit = () => ()

  override def addEngine(id: String, data: A): IO[ValidateError, Unit] = {
    enginesCollection.asInstanceOf[MongoAsyncDao[A]].insertIO(data).map(_ => callback())
  }

  override def updateEngine(id: String, data: A): IO[ValidateError, Unit] = {
    IO.effect(enginesCollection.saveOne("engineId" === id, data))
      .map(_ => callback())
      .mapError(_ => ValidRequestExecutionError())
  }

  override def deleteEngine(id: String): IO[ValidateError, Unit] = {
    IO.effect(enginesCollection.removeOne("engineId" === id)).unit
      .map(_ => callback())
      .mapError(_ => ValidRequestExecutionError())
  }

  override def findEngine(id: String): IO[ValidateError, A] = {
    IO.effect(enginesCollection.findOne("engineId" === id).get)
      .mapError(_ => ValidRequestExecutionError())
  }

  override def listEngines: IO[ValidateError, Iterable[A]] = {
    IO.effect(enginesCollection.findMany())
      .mapError(_ => ValidRequestExecutionError())
  }

  override def onChange(callback: () => Unit): Unit = this.callback = callback

  def codecs: List[CodecProvider]
}
