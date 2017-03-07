package com.actionml.templates.cb

import com.actionml.core.template.{Engine, Params, Query, QueryResult}
import com.typesafe.scalalogging.LazyLogging
import org.json4s.jackson.JsonMethods._
import org.joda.time.DateTime
import org.json4s.{DefaultFormats, Formats}

// Kappa style calls train with each input, may wait for explicit triggering of train for Lambda
class CBEngine(dataset: CBDataset, params: CBEngineParams)
  extends Engine[CBEvent, CBEngineParams, CBQuery, CBQueryResult](dataset, params) with LazyLogging{

  implicit val formats = Formats
  implicit val defaultFormats = DefaultFormats

  def train() = {
    logger.info(s"All data received[${dataset.events.size}]. Start train")
  }

  def input(d: CBEvent): Boolean = {
    logger.info("Got a single CBEvent: " + d)
    logger.info("Kappa learing happens every event, starting now.")
    // todo should validate input value and return Boolean indicating that they were validated
    dataset.append(d)
    train()
    true
  }

  def inputCol(ds: Seq[CBEvent]): Seq[Boolean] = {
    logger.info("Got a Seq of " + ds.size + " Events")
    // todo should validate input values and return Seq of Bools indicating that they were validated
    logger.info("Kappa learing happens every input of events, starting now.")
    dataset.appendAll(ds)
    train()
    Seq(true)
  }

  def parseAndValidateInput(json: String): (CBEvent, Int) = {
    val event = parse(json).extract[CBEvent]
    (event, 0)
  }

  def parseAndValidateQuery(json: String): (CBQuery, Int) = {
    val query = parse(json).extract[CBQuery]
    (query, 0)
  }


  def query(query: CBQuery): CBQueryResult = {
    logger.info(s"Got a query: $query")
    logger.info("Send query result")
    CBQueryResult()
  }

}

case class CBEngineParams(
    id: String = "", // required
    dataset: String = "", // required, readFile now
    maxIter: Int = 100, // the rest of these are VW params
    regParam: Double = 0.0,
    stepSize: Double = 0.1,
    bitPrecision: Int = 24,
    modelName: String = "model.vw",
    namespace: String = "n",
    maxClasses: Int = 3)
  extends Params

/*
Query
{
  "user": "psmith",
  "testGroupId": "testGroupA"
}

Results
{
  "variant": "variantA",
  "testGroupId": "testGroupA"
}

*/
case class CBQuery(
    user: String,
    groupId: String)
  extends Query

case class CBQueryResult(
    variant: String = "",
    groupId: String = "")
  extends QueryResult

case class Properties (
    testPeriodStart: DateTime, // ISO8601 date
    pageVariants: Seq[String], //["17","18"]
    testPeriodEnd: DateTime) // ISO8601 date

case class CBEvent (
    eventId: String,
    event: String,
    entityType: Option[String] = None,
    entityId: Option[String] = None,
    //properties: Option[Map[String, Any]] = None,
    properties: Option[Properties] = None,
    eventTime: String, // ISO8601 date
    creationTime: String) // ISO8601 date
  extends Query
