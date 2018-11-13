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

import com.actionml.core.jobs.JobDescription
import org.apache.spark.SparkContext
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class SparkContextSupportSpec extends FlatSpec with Matchers with BeforeAndAfter {
  System.setProperty("hadoop.home.dir", "/tmp")
  after {
    SparkContextSupport.reset
  }

  "getSparkContext" should "create context at first call" in {
    val config = """{"sparkConf":{"master":"local","appName":"test_app"}}"""
    val futureContext = SparkContextSupport.getSparkContext(config, "engine_id", JobDescription(""))
    Await.result(futureContext, Duration.Inf) shouldBe a [SparkContext]
  }

  it should "sequentially get+release 2 contexts in a row" in {
    val config = """{"sparkConf":{"master":"local","appName":"test_app"}}"""
    val futureContext1 = SparkContextSupport.getSparkContext(config, "engine_id", JobDescription(""))
    val sc1 = Await.result(futureContext1, Duration.Inf)
    SparkContextSupport.stopAndClean(sc1)
    val futureContext2 = SparkContextSupport.getSparkContext(config, "engine_id", JobDescription(""))
    val sc2 = Await.result(futureContext2, Duration.Inf)
    assert(sc2 != sc1)
  }

  it should "return same spark context for same params" in {
    val config = """{"sparkConf":{"master":"local","appName":"test_app"}}"""
    val future1 = SparkContextSupport.getSparkContext(config, "engine_id", JobDescription(""))
    val future2 = SparkContextSupport.getSparkContext(config, "engine_id", JobDescription(""))
    val sc1 = Await.result(future1, Duration.Inf)
    val sc2 = Await.result(future2, Duration.Inf)
    sc1 shouldBe sc2
  }

  it should "promise spark context with different params" in {
    val config1 = """{"sparkConf":{"master":"local","appName":"test_app_1"}}"""
    val future1 = SparkContextSupport.getSparkContext(config1, "engine_id", JobDescription(""))
    val config2 = """{"sparkConf":{"master":"local","appName":"test_app_2"}}"""
    val future2 = SparkContextSupport.getSparkContext(config2, "engine_id", JobDescription(""))
    Await.result(future1, Duration.Inf)
    assert(!future2.isCompleted)
  }

  it should "complete promise for future context with the new context" in {
    val config1 = """{"sparkConf":{"master":"local","appName":"test_app_1"}}"""
    val future1 = SparkContextSupport.getSparkContext(config1, "engine_id", JobDescription(""))
    val config2 = """{"sparkConf":{"master":"local","appName":"test_app_2"}}"""
    val future2 = SparkContextSupport.getSparkContext(config2, "engine_id", JobDescription(""))
    val sc1 = Await.result(future1, Duration.Inf)
    SparkContextSupport.stopAndClean(sc1)
    val sc2 = Await.result(future2, Duration.Inf)
    assert(!sc2.isStopped)
    assert(sc2 != sc1)
  }

}
