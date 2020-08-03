package com.actionml.core

import com.actionml.core.engine.backend.EngineMetadata
import zio.{Has, ZIO}

package object engine {

  type EnginesBackend = Has[EnginesBackend.Service]

  object EnginesBackend {

    trait Service {
      def addEngine(id: String, data: EngineMetadata): HIO[Unit]
      def updateEngine(id: String, data: EngineMetadata): HIO[Unit]
      def deleteEngine(id: String): HIO[Unit]
      def findEngine(id: String): HIO[EngineMetadata]
      def listEngines: HIO[Iterable[EngineMetadata]]
      def modificationEventsQueue: HIO[HQueue[String, (Long, String)]]
      def updateState(harnessId: Long, actionId: String): HIO[Unit]
    }

    def addEngine(id: String, data: EngineMetadata): HIO[Unit] = ZIO.accessM(_.get.addEngine(id, data))
    def updateEngine(id: String, data: EngineMetadata): HIO[Unit] = ZIO.accessM(_.get.updateEngine(id, data))
    def deleteEngine(id: String): HIO[Unit] = ZIO.accessM(_.get.deleteEngine(id))
    def findEngine(id: String): HIO[EngineMetadata] = ZIO.accessM(_.get.findEngine(id))
    def listEngines: HIO[Iterable[EngineMetadata]] = ZIO.accessM(_.get.listEngines)
    def modificationEventsQueue: HIO[HQueue[String, (Long, String)]] = ZIO.accessM(_.get.modificationEventsQueue)
    def updateState(harnessId: Long, actionId: String): HIO[Unit] = ZIO.accessM(_.get.updateState(harnessId, actionId))
  }
}
