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

import java.util.UUID

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import io.circe.syntax._
import org.json4s.JValue
import scaldi.Injector

/**
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  */
class CommandsRouter(implicit inj: Injector) extends BaseRouter {

  override val route: Route = rejectEmptyResponse {
    pathPrefix("commands") {
/*      pathPrefix("findMany") { // this is should be done with GET /commands/ or GET /engines/
        pathPrefix(Segment) { segment ⇒
          pathEndOrSingleSlash {
            segment match {
              case "engines" ⇒ getEngineList
              case "commands" ⇒ getCommandList
            }
          }
        }
      } ~
*/
      pathPrefix("batch-train") {
        runCommand
      } ~ pathPrefix(Segment) { commandId ⇒
        get {
          checkCommand(commandId)
        } ~ delete {
          cancelCommand(commandId)
        }
      }
    }
  }

  private def checkCommand(commandId: String) = extractLog { log ⇒
    log.info("Check command status {}", commandId)
    complete(StatusCodes.OK, """{"status": "ok", "progress": 56}""".asJson)
  }

  private def cancelCommand(commandId: String) = extractLog { log ⇒
    log.info("Check command status {}", commandId)
    complete(StatusCodes.OK, true.asJson)
  }

  private def runCommand = (entity(as[JValue]) & extractLog) { (json, log) ⇒
    log.info("Run command {}", json)
    complete(StatusCodes.OK, UUID.randomUUID().toString)
  }

/* this is done via GET /engines/ and GET /commands/
  private def getCommandList = (get & extractLog) { log ⇒
    log.info("Get commands findMany")
    complete(StatusCodes.OK, Seq.empty[String].asJson)
  }

  private def getEngineList = (get & extractLog) { log ⇒
    log.info("Get engines findMany")
    completeByValidated(StatusCodes.OK) {
      (engineService ? GetEngines("???")).mapTo[Response]
    }
  }
*/

}
