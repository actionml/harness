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
package com.actionml.router.http.routes
import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.actionml.core.config.AppConfig
import com.actionml.router.service.InfoService

import scala.concurrent.ExecutionContext

class InfoRouter(
  override protected val actorSystem: ActorSystem,
  override implicit protected val executor: ExecutionContext,
  override implicit protected val materializer: ActorMaterializer,
  override val config: AppConfig,
  infoService: InfoService
) extends BaseRouter {
  override def route: Route = extractLog {implicit log =>
    path("system") { getSystemInfo } ~
    path("cluster") { getClusterInfo }
  }


  private def getSystemInfo(implicit log: LoggingAdapter): Route = get {
    log.debug("Get system info")
    completeByValidated(StatusCodes.OK) {
      infoService.getSystemInfo
    }
  }

  private def getClusterInfo(implicit log: LoggingAdapter): Route = get {
    log.debug("Get cluster info")
    completeByValidated(StatusCodes.OK) {
      infoService.getClusterInfo
    }
  }
}
