package com.actionml.router.http.routes

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.actionml.router.service.{CreateDataset, DatasetService, DeleteDataset}
import scaldi.Injector

import scala.language.postfixOps

/**
  * Dataset endpoints:
  *
  * Create dataset
  * PUT, POST /datasets/ - Create new empty dataset with generated id
  * PUT, POST /datasets/<datasetId> - Create new empty dataset with <datasetId>
  * Response: HTTP code 201 if the event was successfully created; otherwise, 400
  *
  * Delete dataset
  * DELETE /datasets/<datasetId> - Delete <datasetId> dataset and removes all data.
  * Response: HTTP code 200 if the dataset was successfully deleted; otherwise, 400.
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 28.01.17 12:05
  */

class DatasetsRouter(implicit inj: Injector) extends BaseRouter {

  private val datasetService = injectActorRef[DatasetService]

  val route: Route = rejectEmptyResponse {
    (pathPrefix("datasets") & extractLog) { log ⇒
      pathEndOrSingleSlash {
        createDataset(log)
      } ~ pathPrefix(Segment) { datasetId ⇒
        pathEndOrSingleSlash {
          createDataset(datasetId, log) ~ deleteDataset(datasetId, log)
        }
      }
    }
  }

  private def createDataset(log: LoggingAdapter) = putOrPost {
    log.info("Create empty dataset")
    completeByValidated(StatusCodes.Created) {
      (datasetService ? CreateDataset).mapTo[Response]
    }
  }

  private def createDataset(datasetId: String, log: LoggingAdapter) = putOrPost {
    log.info("Create empty dataset: {}", datasetId)
    complete(StatusCodes.NotFound)
  }

  private def deleteDataset(datasetId: String, log: LoggingAdapter) = delete {
    log.info("Delete dataset: {}", datasetId)
    completeByValidated(StatusCodes.OK) {
      (datasetService ? DeleteDataset(datasetId)).mapTo[Response]
    }
  }

}
