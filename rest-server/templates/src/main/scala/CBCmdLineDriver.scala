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

//package com.actionml.templates.cb

// driver for running Contextual Bandit as an early scaffold
import cats.data.Validated.{Invalid, Valid}
import com.actionml.templates.cb.{CBDataset, CBEngine, CBEngineParams}
import com.typesafe.scalalogging.LazyLogging
import org.json4s._
import org.json4s.jackson.JsonMethods._
import scaldi.akka.AkkaInjectable
import scopt._

import scala.io.Source

case class CBCmdLineDriverConfig(
  modelOut: String = "", // db for model
  inputEvents: String = "", // events readFile
  engineDefJSON: String = ""  // engine.json readFile
)

object CBCmdLineDriver extends App with AkkaInjectable with LazyLogging{

  override def main(args: Array[String]): Unit = {
    val parser = new OptionParser[CBCmdLineDriverConfig]("scopt") {
      head("scopt", "3.x")

      opt[String]('m', "model").action((x, c) =>
        c.copy(modelOut = x)).text("Model storage location, eventually from the Engine config")

      opt[String]('d', "dataset").required().action((x, c) =>
        c.copy(inputEvents = x)).text("Event dataset input location, eventually fome the Engine config")

      opt[String]('e', "engine").required().action((x, c) =>
        c.copy(engineDefJSON = x)).text("Engine config, JSON readFile now, but eventually from shared key/value store")

      help("help").text("prints this usage text")

      note("some notes.")

    }

    // parser.parse returns Option[C]
    parser.parse(args, CBCmdLineDriverConfig()) match {
      case Some(config) =>
        process(config)

      case None =>
      // arguments are bad, error message will have been displayed
    }
  }

  def process( config: CBCmdLineDriverConfig ): Unit = {
    // Infant Template API, create a store, a dataset, and an engine
    // from then all input goes to the engine, which may or may not put it in the dataset using the store for
    // persistence. The engine may train with each new input or may train in batch mode, providing both Kappa
    // and Lambda style learning
    val dataset = new CBDataset("test-resource")
      .destroy()
      .create()

    implicit val formats = DefaultFormats

    val source = Source.fromFile(config.engineDefJSON)
    val engineJSON = try source.mkString finally source.close()

    //json4s style
    val params = parse(engineJSON).extract[CBEngineParams]
    // circe style ?????

    // Todo: params will eventually come from some store that is sharable
    val engine = new CBEngine(dataset, params)

    var errors = 0
    var total = 0
    var good = 0

    Source.fromFile(config.inputEvents).getLines().foreach { line =>

      engine.input(line) match {
        case Valid(_) ⇒ good += 1
        case Invalid(_) ⇒ errors += 1
      }
      total +=1
    }

    logger.info(s"Processed ${total} events, ${errors} were bad in some way")

    val query = """{"user": "pferrel", "group":"group 1" }"""
    engine.query(query) match {
      case Valid(result) ⇒ logger.info(s"Queried and received variant: ${result.variant} groupId: ${result.groupId}")
      case Invalid(error) ⇒ logger.error("Query error {}",error)
    }
  }
}
