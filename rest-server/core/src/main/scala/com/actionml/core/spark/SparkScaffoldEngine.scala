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
import com.actionml.core.engine.Engine
import com.actionml.core.validate.ValidateError

class SparkScaffoldEngine extends Engine {
  private val lambdaAlgo = new SparkLambdaAlgorithm

  override def initAndGet(json: String): Engine = this

  override def destroy(): Unit = ()

  override def query(json: String): Validated[ValidateError, String] = {
    lambdaAlgo.train(json).map(r => s""""result":"$r"""")
  }
}

object SparkScaffoldEngine extends App {
  println(new SparkScaffoldEngine().query(
    """
      |{
      |    "master": "local[2]",
      |    "appName": "scaffold-spark-test",
      |    "database": "harness_auth",
      |    "collection": "users"
      |}
    """.stripMargin))
}