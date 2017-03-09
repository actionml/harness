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

package com.actionml.templates.cb

import com.actionml.core.storage.{Mongo, Store}
import com.actionml.core.template.Dataset
import org.json4s.jackson.JsonMethods._
import org.joda.time.DateTime
import org.json4s.{DefaultFormats, Formats}
import com.actionml.templates.cb.CBEvent

/** DAO for the Contextual Bandit input data
  * There are 2 types of input events for the CB 1) usage events and 2) property change events. The usage events
  * are stored as a potentially very large time ordered collection, the property change events translate
  * into changes to mutable DB objects and so are always up-to-date with the last change made. Another way to
  * look at this is that usage events accumulate until train creates an updateable model then them may be discarded
  * since the model acts as a watermark requiring no history to create predictions. The properties are attached to
  * objects and ony the most recent update is needed so although encoded as events they cause a property change in
  * real time to the object.
  *
  * See the discussion of Kappa Learning here: https://github.com/actionml/pio-kappa/blob/master/kappa-learning.md
  *
  * @param resourceId REST resource-id for input
  * @param store where they are stored, assumes Mongo for the Contextual Bandit
  */
class CBDataset(resourceId: String, store: Mongo) extends Dataset[CBEvent](resourceId, store) {
  // the resourceId is used as a db-id in Mongo, under which will be collections/tables for
  // useage events (which have a datetime index and a TTL) and mutable tables that reflect state of
  // input like groups, and even ML type models created by Vowpal Wabbit's Contextual Bandit algo. The later
  // do not have TTL and reflect the state of the last watermarked model update.

  implicit val formats = DefaultFormats //needed for json4s parsing

  var events = Seq[CBEvent]() // Todo: data will go in a Store eventually

  // todo: we may want to use some operators overloading if this fits some Scala idiom well, but uses a Store so
  // may end up more complicated.

  // add one datum, possibley an CBEvent, to the beginning of the dataset
  def append(datum: CBEvent): Boolean = {
    datum.event(0).toString match {
      case "$" =>
        logger.info(s"Dataset: ${resourceId} got a special event: ${datum.event}")
        datum.event(0) == "$" // modify the object
      case _ =>
        logger.info(s"Dataset: ${resourceId} got a usage event: ${datum.event}")
        events :+ datum
        true // store in events collection, return false if there was an error
    }
  }
  // takes a collection of data to append to the Dataset
  def appendAll(data: Seq[CBEvent]): Seq[Boolean] = {
    data.map(append(_))
  }

  def parseAndValidateInput(json: String): (CBEvent, Int) = {
    val event = parse(json).extract[CBEvent]
    // Todo: check for valid CBEvent, parsing only checks for the right attributes, not the value of those
    (event, 0)
  }


}
