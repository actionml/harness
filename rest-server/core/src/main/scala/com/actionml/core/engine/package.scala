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
import zio.{Has, Queue, UIO, ZIO}

package object engine {

  type EnginesBackend = Has[EnginesBackend.Service]

  object EnginesBackend {

    trait Service {
      def addEngine(id: String, data: EngineMetadata): HIO[Unit]
      def updateEngine(id: String, data: EngineMetadata): HIO[Unit]
      def deleteEngine(id: String): HIO[Unit]
      def findEngine(id: String): HIO[EngineMetadata]
      def listEngines: HIO[Iterable[EngineMetadata]]
      def modificationEventsQueue: HIO[Queue[(Long, String)]]
      def updateState(harnessId: Long, actionId: String): HIO[Unit]
      def close: UIO[Unit]
    }

    def addEngine(id: String, data: EngineMetadata): HIO[Unit] = ZIO.accessM(_.get.addEngine(id, data))
    def updateEngine(id: String, data: EngineMetadata): HIO[Unit] = ZIO.accessM(_.get.updateEngine(id, data))
    def deleteEngine(id: String): HIO[Unit] = ZIO.accessM(_.get.deleteEngine(id))
    def findEngine(id: String): HIO[EngineMetadata] = ZIO.accessM(_.get.findEngine(id))
    def listEngines: HIO[Iterable[EngineMetadata]] = ZIO.accessM(_.get.listEngines)
    def modificationEventsQueue: HIO[Queue[(Long, String)]] = ZIO.accessM(_.get.modificationEventsQueue)
    def updateState(harnessId: Long, actionId: String): HIO[Unit] = ZIO.accessM(_.get.updateState(harnessId, actionId))
  }
}
