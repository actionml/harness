package com.actionml.router.http.routes

import java.util.UUID

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import io.circe.syntax._
import scaldi.Injector

/**
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  */
class CommandsRouter(implicit inj: Injector) extends BaseRouter {

  override val route: Route = rejectEmptyResponse {
    pathPrefix("commands") {
      pathPrefix("list") {
        pathPrefix(Segment) { segment ⇒
          pathEndOrSingleSlash {
            segment match {
              case "engines" ⇒ getEngineList
              case "datasets" ⇒ getDatasetList
              case "commands" ⇒ getCommandList
            }
          }
        }

      } ~ pathPrefix("batch-train") {
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

  private def runCommand = (asJson & extractLog) { (json, log) ⇒
    log.info("Run command {}", json)
    complete(StatusCodes.OK, UUID.randomUUID().toString)
  }

  private def getCommandList = (get & extractLog) { log ⇒
    log.info("Get commands list")
    complete(StatusCodes.OK, Seq.empty[String].asJson)
  }

  private def getEngineList = (get & extractLog) { log ⇒
    log.info("Get engines list")
    complete(StatusCodes.OK, Seq.empty[String].asJson)
  }

  private def getDatasetList = (get & extractLog) { log ⇒
    log.info("Get datasets list")
    complete(StatusCodes.OK, Seq.empty[String].asJson)
  }

}
