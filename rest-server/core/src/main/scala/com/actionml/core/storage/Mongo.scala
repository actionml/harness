package com.actionml.core.storage

import java.net.UnknownHostException

import com.actionml.core.config.AppConfig
import com.actionml.core.dal.mongo.Codecs
import com.mongodb.MongoException
import com.typesafe.config.ConfigFactory
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.connection.ClusterSettings
import org.mongodb.scala.{MongoClient, MongoClientSettings, MongoDatabase, ServerAddress}

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

trait Mongo extends Store {

  private lazy val config = ConfigFactory.load()

  private val master: String = if (config.getString("mongo.host").isEmpty) "localhost" else config.getString("mongo.host")
  private val port: Int = if (config.getInt("mongo.port").toString.isEmpty) 27017 else config.getInt("mongo.port")
  private val uri = s"mongodb://$master:$port"

  implicit val allCollectionObjects = Document("_id" -> Document("$exists" -> true))

  private val mongoConfig = AppConfig.apply.mongoServer

  def client(codecRegistry: CodecRegistry = Codecs.codecRegistry()) = {
    import scala.collection.JavaConversions._
    def settings(codecRegistry: CodecRegistry) = MongoClientSettings.builder
      .clusterSettings(
        ClusterSettings
          .builder()
          .hosts(List(ServerAddress(mongoConfig.host, port = mongoConfig.port))).build)
      .codecRegistry(codecRegistry)
      .build

    MongoClient(settings(codecRegistry))
  }

  def getDatabase(dbName: String, codecProviders: List[CodecProvider] = List.empty): MongoDatabase = {
    client(Codecs.codecRegistry(codecProviders)).getDatabase(dbName)
  }

  override def create(): Mongo = this

  override def destroy(dbName: String): Mongo = {
    try {
      client(Codecs.codecRegistry()).getDatabase(dbName).drop()
    } catch {
      case e: UnknownHostException =>
        logger.error(s"Unknown host for address: $uri", e)
        throw e
      case e: MongoException =>
        logger.error(s"Exception destroying the db for: $dbName", e)
        throw e
      case e: Throwable =>
        logger.error(s"Unknown exception destroying the db for: $dbName", e)
        throw e
    }
    this
  }

}
