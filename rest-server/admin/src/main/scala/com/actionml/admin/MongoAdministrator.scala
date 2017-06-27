package com.actionml.admin

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.storage.Mongo
import com.actionml.core.template.Engine
import com.actionml.core.validate.{JsonParser, ParseError, ValidateError, WrongParams}
import com.actionml.core._
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import salat.dao.SalatDAO

class MongoAdministrator extends Administrator with JsonParser with Mongo {

  lazy val enginesCollection: MongoCollection = connection("harness_meta_store")("engines")
  lazy val commandsCollection: MongoCollection = connection("harness_meta_store")("commands") // async persistent though temporary commands
  var engines = Map.empty[EngineId, Engine]

  drawActionML
  private def newEngineInstance(engineFactory: String): Engine = {
    Class.forName(engineFactory).newInstance().asInstanceOf[Engine]
  }

  // instantiates all stored engine instances with restored state
  override def init() = {
    // ask engines to init
    engines = enginesCollection.find.map { engine =>
      val engineId = engine.get("engineId").toString
      val engineFactory = engine.get("engineFactory").toString
      val params = engine.get("params").toString
      // create each engine passing the params
      engineId -> newEngineInstance(engineFactory).initAndGet(params)
    }.filter(_._2 != null).toMap
    drawInfo("Harness Server Init", Seq(
      ("════════════════════════════════════════", "══════════════════════════════════════"),
      ("Number of Engines: ", engines.size),
      ("Engines: ", engines.map(_._1))))

    this
  }

  def getEngine(engineId: EngineId): Option[Engine] = {
    engines.get(engineId)
  }

  /*
  POST /engines/<engine-id>
  Request Body: JSON for engine configuration engine.json file
    Response Body: description of engine-instance created.
  Success/failure indicated in the HTTP return code
  Action: creates or modifies an existing engine
  */
  def addEngine(json: String): Validated[ValidateError, EngineId] = {
    // val params = parse(json).extract[RequiredEngineParams]
    parseAndValidate[RequiredEngineParams](json).andThen { params =>
      engines = engines + (params.engineId -> newEngineInstance(params.engineFactory).initAndGet(json))
      if (engines(params.engineId) != null) {
        if(enginesCollection.find(MongoDBObject("engineId" -> params.engineId)).size == 1) {
          // re-initialize
          logger.trace(s"Re-initializing engine for resource-id: ${params.engineId} with new params $json")
          val query = MongoDBObject("engineId" -> params.engineId)
          val update = MongoDBObject("$set" -> MongoDBObject("engineFactory" -> params.engineFactory, "params" -> json))
          enginesCollection.findAndModify(query, update)
        } else {
          //add new
          logger.trace(s"Initializing new engine for resource-id: ${params.engineId} with params $json")
          val builder = MongoDBObject.newBuilder
          builder += "engineId" -> params.engineId
          builder += "engineFactory" -> params.engineFactory
          builder += "params" -> json
          enginesCollection += builder.result()

        } // ignores case of too many engine with the same engineId
        Valid(params.engineId)
      } else {
        // init failed
        engines = engines - params.engineId //remove bad engine
        logger.error(s"Failed to re-initializing engine for resource-id: ${params.engineId} with new params $json")
        Invalid(ParseError(s"Failed to re-initializing engine for resource-id: ${params.engineId} with new params $json"))
      }
    }
  }

  override def removeEngine(engineId: String): Validated[ValidateError, Boolean] = {
    if (engines.keySet.contains(engineId)) {
      logger.info(s"Stopped and removed engine and all data for id: $engineId")
      val deadEngine = engines(engineId)
      engines = engines - engineId
      enginesCollection.remove(MongoDBObject("engineId" -> engineId))
      deadEngine.destroy()
      Valid(true)
    } else {
      logger.warn(s"Cannot remove non-existent engine for id: $engineId")
      Invalid(WrongParams(s"Cannot remove non-existent engine for id: $engineId"))
    }
  }

  override def list(resourceType: String): Validated[ValidateError, String] = {
    Valid("\n\n"+engines.mapValues(_.status()).toSeq.mkString("\n\n"))
  }

}
