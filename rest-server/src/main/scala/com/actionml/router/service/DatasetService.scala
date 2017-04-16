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

package com.actionml.router.service

import cats.data.Validated.Invalid
import com.actionml.core.validate.NotImplemented
import com.actionml.router.ActorInjectable
import com.actionml.templates.cb.{CBDataset, CBEngine}
import scaldi.Injector

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 28.01.17 14:44
  */
trait DatasetService extends ActorInjectable

class CBDatasetService(implicit inj: Injector) extends DatasetService{

  private val engine = inject[CBEngine]

  override def receive: Receive = {
    case CreateDataset ⇒
      log.info("Create new dataset")
      // TODO: Not Implemented in dataset
      sender() ! Invalid(NotImplemented("Not Implemented in dataset"))

    case CreateDataset(datasetId) ⇒
      log.info("Create new dataset: {}", datasetId)
      // TODO: Not Implemented in dataset
      sender() ! Invalid(NotImplemented("Not Implemented in dataset"))

    case DeleteDataset(datasetId) ⇒
      log.info("Delete exist dataset: {}", datasetId)
      // TODO: Not Implemented in dataset
      sender() ! Invalid(NotImplemented("Not Implemented in dataset"))
  }
}

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
