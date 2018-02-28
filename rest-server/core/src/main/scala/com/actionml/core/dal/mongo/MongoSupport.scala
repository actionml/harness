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

package com.actionml.core.dal.mongo

import com.actionml.core.config.AppConfig
import org.mongodb.scala.ServerAddress

import scala.concurrent.duration.Duration

// import com.actionml.authserver.config.AppConfig
import com.mongodb.async.client.MongoClientSettings
import com.mongodb.connection.ClusterSettings
import org.mongodb.scala.MongoClient

import scala.reflect.ClassTag

trait MongoSupport {

  def collection[T](dbName: String, collectionName: String)(implicit ct: ClassTag[T]) = {
    //MongoSupport.mongoDatabase.getCollection[T](name)
    // If no DB Name is passed in use the shared one for all engines, this is useful for the shared user case
    // where all engines for a client or some globally shared user database is used
    MongoSupport.mongoClient.getDatabase(dbName).getCollection[T](collectionName)
  }
}

object MongoSupport {

  private val config = AppConfig.apply.mongoServer

  val timeout = Duration(10, "seconds") // Todo: make configurable

  import scala.collection.JavaConversions._

  private val settings = MongoClientSettings.builder
    .clusterSettings(
      ClusterSettings
        .builder()
        .hosts(List(ServerAddress(config.host, port = config.port))).build)
    .codecRegistry(Codecs.codecRegistry)
    .build

  private val mongoClient = MongoClient(settings)

  // Todo: this should be set at runtime to the shared one in the Engine JSON or some global one
  //private val mongoDatabase = mongoClient.getDatabase(config.sharedDB)

  /* todo: doens't work because the macros don't work on a passed in class so need to pass in codecs

  def registerCodec(clazz: Class[_]): Unit = {
    codecRegistries2 = fromRegistries(
      codecRegistries2,
      fromProviders(clazz)
    )
  }
  */
}
