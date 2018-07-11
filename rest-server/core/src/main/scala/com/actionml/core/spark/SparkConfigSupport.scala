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

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.validate.{JsonParser, ValidRequestExecutionError, ValidateError}
import org.apache.spark.{SparkConf, SparkContext}


trait SparkConfigSupport extends JsonParser {

  def createSparkContext(config: String): Validated[ValidateError, SparkContext] = {
    parseAndValidate[SparkConfig](config).andThen { sparkConfig =>
      try {
        val conf = new SparkConf()
          .setMaster(sparkConfig.master)
          .setAppName(sparkConfig.appName)
        conf.set("spark.mongodb.input.uri", "mongodb://localhost/") // todo take it from file config or from incoming json config?
        conf.set("spark.mongodb.input.database", sparkConfig.database)
        conf.set("spark.mongodb.input.collection", sparkConfig.collection)
        Valid(new SparkContext(conf))
      } catch {
        case e: Exception =>
          logger.error("Can't create spark context", e)
          Invalid(ValidRequestExecutionError("Can't create spark context"))
      }
    }
  }
}

case class SparkConfig(master: String, appName: String, database: String, collection: String)
