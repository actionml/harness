package com.actionml.router.service

import com.actionml.ActorInjectable
import scaldi.Injector

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 28.01.17 14:44
  */
trait DatasetService extends ActorInjectable

class EmptyDatasetService(implicit inj: Injector) extends DatasetService{
  override def receive: Receive = {
    case CreateDataset ⇒
      log.info("Create new dataset")
      sender() ! None

    case CreateDataset(datasetId) ⇒
      log.info("Create new dataset: {}", datasetId)
      sender() ! None

    case DeleteDataset(datasetId) ⇒
      log.info("Delete exist dataset: {}", datasetId)
      sender() ! None
  }
}

sealed trait DatasetAction
case object CreateDataset extends DatasetAction
case class CreateDataset(datasetId: String) extends DatasetAction
case class DeleteDataset(datasetId: String) extends DatasetAction
