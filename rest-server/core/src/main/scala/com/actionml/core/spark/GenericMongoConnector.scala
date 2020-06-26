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

package com.actionml.core.spark

import com.actionml.core.store.backends.MongoStorage
import com.mongodb.{MongoClient, MongoClientOptions, MongoClientURI}
import com.mongodb.client.MongoDatabase
import com.mongodb.spark.{MongoClientFactory, MongoConnector}
import com.typesafe.scalalogging.LazyLogging
import org.bson.codecs.configuration.CodecProvider

import scala.reflect.ClassTag


class GenericMongoConnector[T](uri: MongoClientURI, options: MongoClientOptions, codecs: List[CodecProvider], ct: ClassTag[T])
  extends MongoConnector(new GenericMongoClientFactory(uri, options, codecs, ct))
    with Serializable {}

class GenericMongoClientFactory[T](uri: MongoClientURI, options: MongoClientOptions, codecs: List[CodecProvider], ct: ClassTag[T])
  extends MongoClientFactory {

  override def create(): MongoClient = new GenericMongoClient[T](uri, options, codecs, ct)
}

class GenericMongoClient[T](uri: MongoClientURI, options: MongoClientOptions, codecs: List[CodecProvider], ct: ClassTag[T])
  extends MongoClient(uri) with LazyLogging {

  override def getMongoClientOptions: MongoClientOptions = options

  override def getDatabase(databaseName: String): MongoDatabase =
    super.getDatabase(databaseName).withCodecRegistry(MongoStorage.codecRegistry(codecs)(ct))
}
