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

package com.actionml.core

import com.actionml.core.engine.backend.EngineMetadata
import com.actionml.core.jobs.JobDescription
import com.actionml.core.model.{EngineParams, Response}
import zio.{Has, ZIO}

import java.time.Instant
import java.util.UUID

package object engine {

  type EnginesBackend = Has[EnginesBackend.Service]

  object EnginesBackend {

    trait Service {
      def addEngine(id: String, data: EngineMetadata): HIO[Unit]
      def updateEngine(id: String, data: EngineMetadata): HIO[Unit]
      def deleteEngine(id: String): HIO[Unit]
      def watchActions(callback: Action => Unit): HIO[Unit]
      def listNodes: HIO[List[NodeDescription]]
    }

    def addEngine(id: String, data: EngineMetadata): HIO[Unit] = ZIO.accessM(_.get.addEngine(id, data))
    def updateEngine(id: String, data: EngineMetadata): HIO[Unit] = ZIO.accessM(_.get.updateEngine(id, data))
    def deleteEngine(id: String): HIO[Unit] = ZIO.accessM(_.get.deleteEngine(id))
    def watchActions(callback: Action => Unit): HIO[Unit] = ZIO.accessM(_.get.watchActions(callback))
    def listNodes: HIO[List[NodeDescription]] = ZIO.accessM(_.get.listNodes)
  }


  sealed trait Action {
    val id: String
    val timestamp: Instant = Instant.now
    val meta: EngineMetadata
  }
  case class Add(meta: EngineMetadata, id: String = UUID.randomUUID.toString) extends Action
  case class Update(meta: EngineMetadata, id: String = UUID.randomUUID.toString) extends Action
  case class Delete(meta: EngineMetadata, id: String = UUID.randomUUID.toString) extends Action

  object ActionNames extends Enumeration {
    type ActionName = Value

    val add: ActionName = Value("add")
    val update: ActionName = Value("update")
    val delete: ActionName = Value("delete")
  }

  case class NodeDescription(
    node: String,
    engines: Seq[_ <: EngineStatus]
  ) extends Response
}
