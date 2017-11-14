package com.actionml.core.storage

import java.net.UnknownHostException

import com.mongodb.casbah.Imports.{MongoConnection, MongoDBObject, MongoException}
import com.mongodb.casbah.MongoClient
import com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers
import com.typesafe.config.ConfigFactory

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

  val master: String = if (config.getString("mongo.host").isEmpty) "localhost" else config.getString("mongo.host")
  val port: Int = if (config.getInt("mongo.port").toString.isEmpty) 27017 else config.getInt("mongo.port")

  val allCollectionObjects = MongoDBObject("_id" -> MongoDBObject("$exists" -> true))

  lazy val client = MongoClient(master, port)
  @transient lazy val connection = MongoConnection(master, port)

  RegisterJodaTimeConversionHelpers() // registers Joda time conversions used to serialize objects to Mongo

  override def create(): Mongo = this

  override def destroy(dbName: String): Mongo = {
    try {
      client.dropDatabase(dbName)
    } catch {
      case e: UnknownHostException =>
        logger.error(s"Unknown host for address: ${client.address}", e)
        throw e
      case e: MongoException => throw e
      case e: Throwable =>
        logger.error(s"Unknown exception destroying the db for: ${dbName}", e)
        throw e
    }
    this
  }

}
