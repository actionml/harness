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

package com.actionml.engines.ur

import cats.data.Validated
import cats.data.Validated.Valid
import com.actionml.core.engine._
import com.actionml.core.model.{GenericEvent, GenericQuery, GenericQueryResult}
import com.actionml.core.spark.{SparkContextSupport, SparkMongoSupport}
import com.actionml.core.validate.{JsonParser, ValidateError}
import org.apache.spark.{SparkContext, rdd}
import org.bson.Document

/** Scafolding for a Kappa Algorithm, change with KappaAlgorithm[T] to with LambdaAlgorithm[T] to switch to Lambda,
  * and mixing is allowed since they each just add either real time "input" or batch "train" methods. It is sometimes
  * possible to make use of real time input in a LambdaAlgorithm such as the Universal Recommender making real time
  * changes to item attributes directly in the model rather than waiting for a training task.
  * This is not the minimal Template because many methods are implemented generically in the
  * base classes but is better used as a starting point for new Engines.
  */
class URAlgorithm[T] private (initParams: String, dataset: Dataset[T]) extends Algorithm[GenericQuery, GenericQueryResult]
  with LambdaAlgorithm[T] with SparkContextSupport with SparkMongoSupport with JsonParser {
  import URAlgorithm._

  private var sparkContext: Validated[ValidateError, SparkContext] = _

  /** Be careful to call super.init(...) here to properly make some Engine values available in scope */
  override def init(engine: Engine): Validated[ValidateError, Boolean] = {
    super.init(engine).andThen { _ =>
      parseAndValidate[UREngine.URAlgorithmParams](initParams, transform = _ \ "algorithm").andThen { p =>
        // p is just the validated algo params from the engine's params json file.
        if (sparkContext != null) sparkContext.foreach { sc =>
          sc.stop
        }

        Valid(true)
      }
    }
  }

  override def destroy(): Unit = {
  }

  override def input(datum: T): Validated[ValidateError, Boolean] = {
    logger.info("Some events may cause the UR to immediately modify the model, like property change events." +
      " This is where that will be done")
    Valid(true)
  }

  override def train(): Validated[ValidateError, String] = {
    process()
  }

  override def process(): Validated[ValidateError, String] = {
    def myTrainFunction: Iterator[Document] => String = _.mkString(" -- ")

    sparkContext = createSparkContext(
      appName = dataset.engineId,
      dbName = dataset.dbName,
      collection = dataset.collection,
      config = initParams)

    logger.debug(s"Starting train $this with spark $sparkContext")

    /*
    sparkContext.andThen { sc =>

      val rdd = sc.makeRDD((1 to 10000).toSeq))
      val result = rdd.ma

      Valid("URAlgorithm model creation queued for processing on the Spark cluster")
    }
    */

    sparkContext.andThen { sc =>
      val rdd = createRdd(sc)
      val result = sc.runJob(rdd, myTrainFunction)
      println("********************************")
      println(result)
      println("********************************")
      Valid(
        """
          |{
          |  "Comment": "Made it to URAlgorithm.train"
          |  "jobId": "replace with actual Spark + YARN job-id"
          |  "other": "other useful info"
          |}
        """.stripMargin
      )
    }


  }

  def query(query: GenericQuery): GenericQueryResult = {
    GenericQueryResult()
  }

}

object URAlgorithm {

  def apply[T](engine: Engine, initParams: String, dataset: Dataset[T]): URAlgorithm[T] = {
    val algo = new URAlgorithm[T](initParams, dataset)
    algo.init(engine)
    algo
  }

}

