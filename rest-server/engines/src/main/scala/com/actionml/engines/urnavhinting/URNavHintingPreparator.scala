package com.actionml.engines.urnavhinting

import com.actionml.core.spark.SparkMongoSupport
import com.actionml.engines.urnavhinting.URNavHintingAlgorithm.{PreparedData, TrainingData}
import com.actionml.engines.urnavhinting.URNavHintingEngine.URNavHintingEvent
import com.typesafe.scalalogging.LazyLogging
import org.apache.mahout.math.RandomAccessSparseVector
import org.apache.mahout.math.indexeddataset.BiDictionary
import org.apache.mahout.sparkbindings.indexeddataset.IndexedDatasetSpark
import org.apache.mahout.sparkbindings.{DrmRdd, drmWrap}
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

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

/** Partitions off creation of Mahout data structures from stored data in a DB */
object URNavHintingPreparator extends LazyLogging with SparkMongoSupport {

  /** Reads events from PEventStore and create and RDD for each */
  def mkTraining(eventNames: Seq[String], eventsRDD: RDD[URNavHintingEvent])(implicit sc: SparkContext): TrainingData = {

    //val eventNames = dsp.eventNames

    // beware! the following call most likely will alter the event stream in the DB!
    //cleanPersistedPEvents(sc) // broken in apache-pio v0.10.0-incubating it erases all data!!!!!!

    /*
    val eventsRDD = PEventStore.find(
      appName = dsp.appName,
      entityType = Some("user"),
      eventNames = Some(eventNames),
      targetEntityType = Some(Some("item")))(sc).repartition(sc.defaultParallelism)
    */

    // now separate the events by event name
    val eventRDDs: Seq[(ActionID, RDD[(UserID, ItemID)])] = eventNames.map { eventName =>
      val singleEventRDD = eventsRDD
        .filter( e => e.event == eventName )
        .map { e =>
          (e.entityId, e.targetEntityId.getOrElse(""))
        }

      (eventName, singleEventRDD)
    } filterNot { case (_, singleEventRDD) => singleEventRDD.isEmpty() }

    //val collectedRdds = eventRDDs.map(_._2.collect())
    logger.info(s"Received events ${eventRDDs.map(_._1)}")

    /*
    // aggregating all $set/$unsets for metadata fields, which are attached to items
    val fieldsRDD: RDD[(ItemID, PropertyMap)] = PEventStore.aggregateProperties(
      appName = dsp.appName,
      entityType = "item")(sc).repartition(sc.defaultParallelism)
    //    logger.debug(s"FieldsRDD\n${fieldsRDD.take(25).mkString("\n")}")
    */

    val fieldsRDD: RDD[(ItemID, PropertyMap)] = sc.emptyRDD[(ItemID, PropertyMap)]

    // Have a list of (actionName, RDD), for each action
    // todo: some day allow data to be content, which requires rethinking how to use EventStore
    TrainingData(eventRDDs, fieldsRDD, Some(1))
  }

  /** Prepares Mahout IndexedDatasetSpark from the URNavHintingEvent collection in Mongo
    * by first converting in to separate RDD[(String, String)] from each eventName passed in
    * then we supply a companion object that constructs the IndexedDatasetSpark from the
    * String Pair RDD. The pairs are (user-id, item-id) for a given event, see the CCO
    * algorithm to understand how this is used: todo: create a refernce for the algo
    * @param eventNames From eventNames or indicators in the engine's JSON config
    * @param sc an active SparkContext
    * @return Seq of (eventName, IndexedDatasetSpark) todo: should be a Map, legacy from PIO
    */
  def prepareData(
    trainingData: TrainingData)
    (implicit sc: SparkContext): PreparedData = {
    // now that we have all actions in separate RDDs we must merge any user dictionaries and
    // make sure the same user ids map to the correct events
    var userDictionary: Option[BiDictionary] = None

    val indexedDatasets = trainingData.actions.map {
      case (eventName, eventRDD) =>

        // passing in previous row dictionary will use the values if they exist
        // and append any new ids, so after all are constructed we have all user ids in the last dictionary
        logger.info("EventName: " + eventName)
        // logger.info(s"first eventName is ${trainingData.actions.head._1.toString}")
        val ids = if (eventName == trainingData.actions.head._1.toString && trainingData.minEventsPerUser.nonEmpty) {
          val dIDS = IndexedDatasetSparkFactory(eventRDD, trainingData.minEventsPerUser.get)(sc)
          logger.info(s"Downsampled  users for minEventsPerUser: ${trainingData.minEventsPerUser}, eventName: $eventName" +
            s" number of passing user-ids: ${dIDS.rowIDs.size}")
          logger.info(s"Dimensions rows : ${dIDS.matrix.nrow.toString} columns: ${dIDS.matrix.ncol.toString}")
          // we have removed underactive users now remove the items they were the only to interact with
          val ddIDS = IndexedDatasetSpark(eventRDD, Some(dIDS.rowIDs))(sc) // use the downsampled rows to downnsample
          userDictionary = Some(ddIDS.rowIDs)
          logger.info(s"Downsampled columns for users who pass minEventPerUser: ${trainingData.minEventsPerUser}, " +
            s"eventName: $eventName number of user-ids: ${userDictionary.get.size}")
          logger.info(s"Dimensions rows : ${ddIDS.matrix.nrow.toString} columns: ${ddIDS.matrix.ncol.toString}")
          //ddIDS.dfsWrite(eventName.toString, DefaultIndexedDatasetWriteSchema)(new SparkDistributedContext(sc))
          ddIDS
        } else {
          //logger.info(s"IndexedDatasetSpark for eventName: $eventName User ids: $userDictionary")
          val dIDS = IndexedDatasetSpark(eventRDD, userDictionary)(sc)
          userDictionary = Some(dIDS.rowIDs)
          //dIDS.dfsWrite(eventName.toString, DefaultIndexedDatasetWriteSchema)(new SparkDistributedContext(sc))
          logger.info(s"Dimensions rows : ${dIDS.matrix.nrow.toString} columns: ${dIDS.matrix.ncol.toString}")
          logger.info(s"Number of user-ids after creation: ${userDictionary.get.size}")
          dIDS
        }

        (eventName, ids)

    }

    /*
    val fieldsRDD: RDD[(ItemID, PropertyMap)] = trainingData.fieldsRDD.map {
      case (itemId, propMap) => itemId -> propMap.fields
    }
    */

    PreparedData(indexedDatasets, trainingData.fieldsRDD)
  }
}

/** Companion Object to construct an IndexedDatasetSpark from String Pair RDDs */
object IndexedDatasetSparkFactory {

  /** Constructor for primary indicator where the userDictionary is built */
  def apply(
    elements: RDD[(String, String)],
    minEventsPerUser: Int)(implicit sc: SparkContext): IndexedDatasetSpark = {
    // todo: a further optimization is to return any broadcast dictionaries so they can be passed in and
    // do not get broadcast again. At present there may be duplicate broadcasts.

    // create separate collections of rowID and columnID tokens
    // use the dictionary passed in or create one from the element ids
    // broadcast the correct row id BiDictionary
    val rowIDDictionary = new BiDictionary(elements.map { case (rowID, _) => rowID }.distinct().collect())
    val rowIDDictionary_bcast = sc.broadcast(rowIDDictionary)
    val filteredElements = elements.filter {
      case (rowID, _) =>
        rowIDDictionary_bcast.value.contains(rowID)
    }

    // item ids are always taken from the RDD passed in
    // todo: an optimization it to pass in a dictionary or item ids if it is the same as an existing one
    val itemIDs = filteredElements.map { case (_, itemID) => itemID }.distinct().collect()

    val itemIDDictionary = new BiDictionary(keys = itemIDs)
    val itemIDDictionary_bcast = sc.broadcast(itemIDDictionary)

    val ncol = itemIDDictionary.size
    //val nrow = rowIDDictionary.size
    val minEvents = minEventsPerUser

    val downsampledInteractions = elements.groupByKey().filter {
      case (userId, items) =>
        items.size >= minEvents
    }

    val downsampledUserIDDictionary = new BiDictionary(downsampledInteractions.map {
      case (userID, _) =>
        userID
    }.distinct().collect())
    val downsampledUserIDDictionary_bcast = sc.broadcast(downsampledUserIDDictionary)

    val interactions =
      downsampledInteractions.map {
        case (userID, items) =>
          val userKey = downsampledUserIDDictionary_bcast.value.get(userID).get
          val vector = new RandomAccessSparseVector(ncol)
          for (item <- items) {
            vector.setQuick(itemIDDictionary_bcast.value.get(item).get, 1.0d)
          }

          userKey -> vector
      }.asInstanceOf[DrmRdd[Int]].repartition(sc.defaultParallelism)

    // wrap the DrmRdd and a CheckpointedDrm, which can be used anywhere a DrmLike[Int] is needed
    val drmInteractions = drmWrap[Int](interactions)

    //drmInteractions.newRowCardinality(rowIDDictionary.size)

    new IndexedDatasetSpark(drmInteractions.newRowCardinality(rowIDDictionary.size), downsampledUserIDDictionary, itemIDDictionary)
  }

  /** Another constructor for secondary indicators where the userDictionary is already known */
  def apply(
    elements: RDD[(String, String)],
    existingRowIDs: Option[BiDictionary])(implicit sc: SparkContext): IndexedDatasetSpark = {
    // todo: a further optimization is to return any broadcast dictionaries so they can be passed in and
    // do not get broadcast again. At present there may be duplicate broadcasts.

    // create separate collections of rowID and columnID tokens
    // use the dictionary passed in or create one from the element ids
    // broadcast the correct row id BiDictionary
    val (filteredElements, rowIDDictionary_bcast, rowIDDictionary) = if (existingRowIDs.isEmpty) {
      val newRowIDDictionary = new BiDictionary(elements.map { case (rowID, _) => rowID }.distinct().collect())
      val newRowIDDictionary_bcast = sc.broadcast(newRowIDDictionary)
      (elements, newRowIDDictionary_bcast, newRowIDDictionary)
    } else {
      val existingRowIDDictionary_bcast = sc.broadcast(existingRowIDs.get)
      val elementsRDD = elements.filter {
        case (rowID, _) =>
          existingRowIDDictionary_bcast.value.contains(rowID)
      }
      (elementsRDD, existingRowIDDictionary_bcast, existingRowIDs.get)
    }

    // column ids are always taken from the RDD passed in
    // todo: an optimization it to pass in a dictionary or column ids if it is the same as an existing one
    val columnIDs = filteredElements.map { case (_, columnID) => columnID }.distinct().collect()

    val columnIDDictionary = new BiDictionary(keys = columnIDs)
    val columnIDDictionary_bcast = sc.broadcast(columnIDDictionary)

    val ncol = columnIDDictionary.size
    //val nrow = rowIDDictionary.size

    val indexedInteractions =
      filteredElements.map {
        case (rowID, columnID) =>
          val rowIndex = rowIDDictionary_bcast.value.getOrElse(rowID, -1)
          val columnIndex = columnIDDictionary_bcast.value.getOrElse(columnID, -1)

          rowIndex -> columnIndex
      }
        // group by IDs to form row vectors
        .groupByKey().map {
          case (rowIndex, columnIndexes) =>
            val row = new RandomAccessSparseVector(ncol)
            for (columnIndex <- columnIndexes) {
              row.setQuick(columnIndex, 1.0)
            }
            rowIndex -> row
        }.asInstanceOf[DrmRdd[Int]].repartition(sc.defaultParallelism)

    // wrap the DrmRdd and a CheckpointedDrm, which can be used anywhere a DrmLike[Int] is needed
    val drmInteractions = drmWrap[Int](indexedInteractions)

    new IndexedDatasetSpark(drmInteractions.newRowCardinality(rowIDDictionary.size), rowIDDictionary, columnIDDictionary)
  }

}

