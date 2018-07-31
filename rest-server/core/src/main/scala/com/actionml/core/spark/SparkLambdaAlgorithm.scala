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
import com.actionml.core.engine.LambdaAlgorithm
import com.actionml.core.validate.{JsonParser, ValidRequestExecutionError, ValidateError}
import com.mongodb.spark.MongoSpark
import org.apache.spark.SparkContext
import org.bson.Document

class SparkLambdaAlgorithm(sparkContextConfig: String) extends LambdaAlgorithm[String] with SparkContextSupport with JsonParser {

  private lazy val validatedSparkContext: Validated[ValidateError, SparkContext] = createSparkContext(sparkContextConfig)

  override def train(): Validated[ValidateError, String] = validatedSparkContext.andThen { sparkContext =>
    try {
      val rdd = MongoSpark.load[Document](sparkContext)
      Valid(checkResult(sparkContext.runJob(rdd, processPartition)))
    } catch {
      case e: Exception =>
        logger.error(s"Can't run spark job for config $sparkContextConfig", e)
        Invalid(ValidRequestExecutionError("Can't run Spark job"))
    }
  }


  private def processPartition: Iterator[Document] => String = i => {
    i.foldLeft("")(_ + _)
  }

  private def checkResult[String](a: Array[String]) = a.foldLeft("")(_ + _).trim
}
