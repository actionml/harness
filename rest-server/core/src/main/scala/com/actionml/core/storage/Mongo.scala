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

class Mongo(m: String = "localhost", p: Int = 27017, n: String = "some-resource-id") extends Store {

  val master: String = m
  val port: Int = p
  val dbName: String = n
  lazy val client = MongoClient(master, port)
  val store = this

  // should only be called from trusted source like the CLI!
  def create(): Mongo = {
    this
  }

  def destroy(): Mongo = {
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

