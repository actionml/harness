/*
 * Copyright ActionML, LLC under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
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

package com.actionml.core.storage

import java.net.UnknownHostException

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoClient
import com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers
import com.typesafe.config.ConfigFactory

trait Mongo extends Store {

  private lazy val config = ConfigFactory.load()

  val master: String = if (config.getString("mongo.host").isEmpty) "localhost" else config.getString("mongo.host")
  val port: Int = if (config.getInt("mongo.port").toString.isEmpty) 27017 else config.getInt("mongo.port")

  implicit val allCollectionObjects = MongoDBObject("_id" -> MongoDBObject("$exists" -> true))

  lazy val client = Mongo.client(master, port)

  lazy val connection = Mongo.connection(master, port)

  RegisterJodaTimeConversionHelpers() // registers Joda time conversions used to serialize objects to Mongo

  override def create(): Mongo = this

  override def destroy(dbName: String): Mongo = {
    try {
      client.dropDatabase(dbName)
    } catch {
      case e: UnknownHostException =>
        logger.error(s"Unknown host for address: ${client.address}", e)
        throw e
      case e: MongoException =>
        logger.error(s"Exception destroying the db for: ${dbName}", e)
        throw e
      case e: Throwable =>
        logger.error(s"Unknown exception destroying the db for: ${dbName}", e)
        throw e
    }
    this
  }

}

// Singleton object so we create only one client or connection to use Mongo connection pooling
object Mongo {

  // TODO: should use apply to get rid of vars
  var currentClient: Option[MongoClient] = None
  var currentConnection: Option[MongoConnection] = None

  def client(master: String = "localhost", port: Int = 21017): MongoClient = {
    if(currentClient.isEmpty){
      currentClient = Some(MongoClient(master, port))
    }
    currentClient.get
  }

  // TODO: does this create too many connections? Switch to new Mongo Scala lib to avoid Casbah
  def connection(master: String = "localhost", port: Int = 21017): MongoConnection = {
    if(currentConnection.isEmpty){
      currentConnection = Some(MongoConnection(master, port))
    }
    currentConnection.get
  }

}

// Todo: should we support username, password connections to MongoDB?
// Automatically detect SCRAM-SHA-1 or Challenge Response protocol
// val server = new ServerAddress("localhost", 27017)
// val credentials = MongoCredential.createCredential(userName, source, password)
// val mongoClient = MongoClient(server, List(credentials))
// Todo: also may want to connect to all mongos replica sets
// val rs1 = new ServerAddress("localhost", 27017)
// val rs2 = new ServerAddress("localhost", 27018)
// val rs3 = new ServerAddress("localhost", 27019)
// val mongoClient = MongoClient(List(rs1, rs2, rs3))

