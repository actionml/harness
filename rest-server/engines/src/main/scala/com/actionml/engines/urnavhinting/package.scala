package com.actionml.engines

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

import scala.collection.JavaConversions._
import org.apache.mahout.sparkbindings.SparkDistributedContext
import org.apache.mahout.sparkbindings.indexeddataset.IndexedDatasetSpark
import org.apache.mahout.sparkbindings._
import org.apache.spark.rdd.RDD
//import org.json4s._


package object urnavhinting {

  type ActionID = String
  type UserID = String
  type ItemID = String
  type PropertyMap = Map[String, Any]

  implicit class IndexedDatasetConversions(val indexedDataset: IndexedDatasetSpark) {

    def toStringMapRDD(actionName: String): RDD[(String, Map[String, Any])] = {

      //val collectedRdd = indexedDataset.matrix.rdd.collect()

      //val matrix = indexedDataset.matrix.checkpoint()
      val rowIDReverseDictionary = indexedDataset.rowIDs.inverse // precalc the inverse
      implicit val sc = indexedDataset.matrix.context.asInstanceOf[SparkDistributedContext].sc
      val rowIDReverseDictionary_bcast = sc.broadcast(rowIDReverseDictionary)

      val columnIDReverseDictionary = indexedDataset.columnIDs.inverse // precalc the inverse
      val columnIDReverseDictionary_bcast = sc.broadcast(columnIDReverseDictionary)

      // may want to mapPartition and create bulk updates as a slight optimization
      // creates an RDD of (itemID, Map[correlatorName, list-of-correlator-values])
      val retval = indexedDataset.matrix.rdd.map[(String, Map[String, Any])] {
        case (rowNum, itemVector) =>

          // turn non-zeros into list for sorting
          val vector = itemVector.nonZeroes.map { element =>
            (element.index(), element.get())
          }.toList.sortBy(element => -element._2) map { item =>
            columnIDReverseDictionary_bcast.value.getOrElse(item._1, "") // should always be in the dictionary
          }

          val itemID = rowIDReverseDictionary_bcast.value.getOrElse(rowNum, "INVALID_ITEM_ID")

          (itemID, Map(actionName -> vector))
      }

      //val collectedI = retval.collect()
      //val size = collectedI.size
      retval
    }
  }

}
