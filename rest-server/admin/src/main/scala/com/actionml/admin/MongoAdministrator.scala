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

package com.actionml.admin

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.core._
import com.actionml.core.model.GenericEngineParams
import com.actionml.core.storage.Mongo
import com.actionml.core.template.Engine
import com.actionml.core.validate._
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.BsonString
import org.mongodb.scala.bson.collection.immutable.Document
import scaldi.{Injectable, Injector, Module}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class MongoAdministrator(override implicit val injector: Module)
  extends Administrator with JsonParser with Mongo with Injectable {

  lazy val enginesCollection: MongoCollection[Document] = getDatabase("harness_meta_store").getCollection("engines")
//  lazy val commandsCollection: MongoCollection[Engine] = getDatabase("harness_meta_store").getCollection("commands") // async persistent though temporary commands
  var engines = Map.empty[EngineId, Engine]
  private val ec = inject[ExecutionContext]

  drawActionML
  private def newEngineInstance(engineFactory: String): Engine = {
    //Class.forName(engineFactory).newInstance().asInstanceOf[Engine]
    //val c = Class.getDeclaredConstructor(String.class).newInstance("HERESMYARG")
    val v = Class.forName(engineFactory).getConstructors
    val c = Injector.getClass
    val c2 = classOf[Injector]
    Class.forName(engineFactory).getDeclaredConstructor(classOf[Injector]).newInstance(injector).asInstanceOf[Engine]
  }

  // instantiates all stored engine instances with restored state
  override def init(): MongoAdministrator = {
    implicit val _ = ec
    def initEngine(engineDoc: Document): Future[Try[Engine]] = {
      Future.fromTry(Try(newEngineInstance(engineDoc.get("engineFactory").get.asString.getValue))).flatMap { engine =>
        val params = engineDoc.get("params").get.asString.getValue
        engine.initAndGet(params)
          .map(Success(_))
          .recoverWith { case error =>
            // it is possible that previously valid metadata is now bad, the Engine code must have changed
            logger.error(s"Error creating engineId: ${engine.engineId} from $params" +
              s"\n\nTry deleting the engine instance by hand and re-adding to start fresh or fix the JSON config if there " +
              s"is an error in it. Note: " +
              s"this may happen when code for one version of the Engine has chosen to not be backwards compatible.")
            // Todo: we need a way to cleanup in this situation
            enginesCollection.deleteOne(Document("engineId" -> engine.engineId)).toFuture().map(_ => Failure(error))
            // can't do this because the instance is null: deadEngine.destroy(), maybe we need a compan ion object with a cleanup function?
          }
      }
    }
    // ask engines to init
    val f = (for {
      engines <- enginesCollection.find.toFuture
      initializedEngines <- Future.sequence(engines.map(initEngine))
    } yield initializedEngines.collect { case Success(e) => e })
      .map { engines =>
        drawInfo("Harness Server Init", Seq(
          ("════════════════════════════════════════", "══════════════════════════════════════"),
          ("Number of Engines: ", engines.size),
          ("Engines: ", engines.map(_.engineId))))
        this.engines = engines.map(e => e.engineId -> e).toMap
        this
      }
    Await.result(f, 5.seconds)
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
  def addEngine(json: String)(implicit ec: ExecutionContext): Future[Validated[ValidateError, EngineId]] = {
    // val params = parse(json).extract[GenericEngineParams]
    val f = parseAndValidate[GenericEngineParams](json).fold(e => Future.successful(Invalid(e)), { params =>
      for {
        newEngine <- newEngineInstance(params.engineFactory).initAndGet(json)
        enginesWithSameId <- enginesCollection.find(Document("engineId" -> params.engineId)).toFuture
        result <- if (enginesWithSameId.size == 1) {
          // re-initialize
          logger.trace(s"Re-initializing engine for resource-id: ${params.engineId} with new params $json")
          val query = Document("engineId" -> params.engineId)
          val update = Document("$set" -> Document("engineFactory" -> params.engineFactory, "params" -> json))
          enginesCollection.findOneAndUpdate(query, update).toFuture.map(_ => Valid(params.engineId))
        } else {
          //add new
          engines += params.engineId -> newEngine
          logger.trace(s"Initializing new engine for resource-id: ${params.engineId} with params $json")
          val data = Document.fromSeq(Seq(
            "engineId" -> BsonString(params.engineId),
            "engineFactory" -> BsonString(params.engineFactory),
            "params" -> BsonString(json)
          ))
          enginesCollection.insertOne(data).toFuture.map(_ => Valid(params.engineId))
        }
      } yield result
    })
    f.recover { case _ => Invalid(ParseError(s"Unable to create Engine: $json, the config JSON seems to be in error")) }
    f
  }

  override def removeEngine(engineId: String)(implicit ec: ExecutionContext): Future[Validated[ValidateError, Boolean]] = {
    if (engines.contains(engineId)) {
      logger.info(s"Stopped and removed engine and all data for id: $engineId")
      val deadEngine = engines(engineId)
      engines = engines - engineId
      for {
        _ <- enginesCollection.deleteOne(Document("engineId" -> engineId)).toFuture
        _ <- deadEngine.destroy()
      } yield Valid(true)
    } else {
      logger.warn(s"Cannot remove non-existent engine for id: $engineId")
      Future.successful(Invalid(WrongParams(s"Cannot remove non-existent engine for id: $engineId")))
    }
  }

  override def status(resourceId: Option[String] = None): Validated[ValidateError, String] = {
    if (resourceId.nonEmpty) {
      if (engines.contains(resourceId.get)) {
        logger.trace(s"Getting status for ${resourceId.get}")
        Valid(engines(resourceId.get).status().toString)
      } else {
        logger.error(s"Non-existent engine-id: ${resourceId.get}")
        Invalid(WrongParams(s"Non-existent engine-id: ${resourceId.get}"))
      }
    } else {
      logger.trace("Getting status for all Engines")
      Valid(engines.mapValues(_.status()).toSeq.mkString("\n"))
    }
  }

  override def updateEngine(json: String)(implicit ec: ExecutionContext): Future[Validated[ValidateError, String]] = {
    parseAndValidate[GenericEngineParams](json).fold(e => Future.successful(Invalid(e)), { params =>
      engines.get(params.engineId).map { existingEngine =>
        logger.trace(s"Re-initializing engine for resource-id: ${params.engineId} with new params $json")
        val query = Document("engineId" -> params.engineId)
        val update = Document("$set" -> Document("engineFactory" -> params.engineFactory, "params" -> json))
        for {
          _ <- enginesCollection.updateOne(query, update).toFuture
          result <- existingEngine.init(json).map { _.andThen(_ => Valid(
            """{
              |  "comment":"Get engine status to see what was changed."
              |}
            """.stripMargin))}
        } yield result
      }.getOrElse(Future.successful(Invalid(WrongParams(s"Unable to update Engine: ${params.engineId}, the engine does not exist"))))
    })
  }

}

