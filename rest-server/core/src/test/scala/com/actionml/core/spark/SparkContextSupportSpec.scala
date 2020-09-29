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

import cats.data.Validated.Invalid
import com.actionml.core.validate.WrongParams
import org.apache.spark.SparkContext
import org.scalatest.{FlatSpec, Ignore, Matchers, OneInstancePerTest}

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}
import scala.util.Success

@Ignore
class SparkContextSupportSpec extends FlatSpec with Matchers with OneInstancePerTest {
  private val timeout = 10.seconds
  System.setProperty("hadoop.home.dir", "/tmp")

  "getSparkContext" should "create context at first call" in {
    val config = """{"sparkConf":{"master":"local","appName":"test_app"}}"""
    val p = Promise[SparkContext]()
    SparkContextSupport.withSparkContext(config, "engine_id")(sc => p.complete(Success(sc)).future)
    val sc = Await.result(p.future, timeout)
    sc shouldBe a[SparkContext]
    assert(sc.isStopped)
  }

  it should "return error if train is running" in {
    val config = """{"sparkConf":{"master":"local","appName":"test_app"}}"""
    val p1 = Promise[SparkContext]()
    val p2 = Promise[SparkContext]()
    SparkContextSupport.withSparkContext(config, "engine_id")(sc => p1.complete(Success(sc)).future)
    val error = SparkContextSupport.withSparkContext(config, "engine_id")(sc => p2.complete(Success(sc)).future)
    val sc = Await.result(p1.future, timeout)
    sc shouldBe a[SparkContext]
    assert(!p2.isCompleted)
    error shouldBe a[Invalid[WrongParams]]
  }

}
