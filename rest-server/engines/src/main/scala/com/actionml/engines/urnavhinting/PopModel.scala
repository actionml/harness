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

package com.actionml.engines.urnavhinting

import com.actionml.core.model.GenericEvent
import com.actionml.core.validate.JsonParser
import com.actionml.engines.urnavhinting.URNavHintingEngine.URNavHintingEvent
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{DateTime, Interval}

import scala.language.postfixOps
import scala.util.Random

object RankingFieldName {
  val UserRank = "userRank"
  val UniqueRank = "uniqueRank"
  val PopRank = "popRank"
  val TrendRank = "trendRank"
  val HotRank = "hotRank"
  val UnknownRank = "unknownRank"
  def toSeq: Seq[String] = Seq(UserRank, UniqueRank, PopRank, TrendRank, HotRank)
  override def toString: String = s"$UserRank, $UniqueRank, $PopRank, $TrendRank, $HotRank"
}

object RankingType {
  val Popular = "popular"
  val Trending = "trending"
  val Hot = "hot"
  val UserDefined = "userDefined"
  val Random = "random"
  def toSeq: Seq[String] = Seq(Popular, Trending, Hot, UserDefined, Random)
  override def toString: String = s"$Popular, $Trending, $Hot, $UserDefined, $Random"
}

class PopModel(implicit sc: SparkContext)
  extends JsonParser {

  def calc(
    modelName: String,
    eventNames: Seq[String],
    eventsRdd: RDD[URNavHintingEvent],
    duration: Int = 0,
    offsetDate: Option[String] = None): RDD[(String, Double)] = {

    // todo: make end manditory and fill it with "now" upstream if not specified, will simplify logic here
    // end should always be 'now' except in unusual conditions like for testing
    val end = if (offsetDate.isEmpty) DateTime.now else {
      try {
        ISODateTimeFormat.dateTimeParser().parseDateTime(offsetDate.get)
      } catch {
        case e: IllegalArgumentException =>
          logger.warn("Bad end for popModel: " + offsetDate.get + " using 'now'")
          DateTime.now
      }
    }

    val interval = new Interval(end.minusSeconds(duration), end)

    // based on type of popularity model return a set of (item-id, ranking-number) for all items
    logger.info(s"PopModel $modelName using end: $end, and duration: $duration, interval: $interval")

    // if None? debatable, this is either an error or may need to default to popular, why call popModel otherwise
    modelName match {
      case RankingType.Popular     => calcPopular(eventsRdd, eventNames, interval)
      case unknownRankingType =>
        logger.warn(
          s"""
             |Bad rankings param type=[$unknownRankingType] in engine definition params, possibly a bad json value.
             |Use one of the available parameter values ($RankingType).""".stripMargin)
        sc.emptyRDD
    }

  }

  /** Creates a rank from the number of named events per item for the duration */
  def calcPopular(
    eventsRdd: RDD[URNavHintingEvent],
    eventNames: Seq[String],
    interval: Interval): RDD[(String, Double)] = {
    eventsRdd.map { e => (e.targetEntityId, e.event) }
      .groupByKey()
      .map { case (itemID, itEvents) => (itemID.get, itEvents.size.toDouble) }
      .reduceByKey(_ + _) // make this a double in Elaseticsearch)
  }

}

object PopModel {

  val nameByType: Map[String, String] = Map(
    RankingType.Popular -> RankingFieldName.PopRank,
    RankingType.Trending -> RankingFieldName.TrendRank,
    RankingType.Hot -> RankingFieldName.HotRank,
    RankingType.UserDefined -> RankingFieldName.UserRank,
    RankingType.Random -> RankingFieldName.UniqueRank).withDefaultValue(RankingFieldName.UnknownRank)

}
