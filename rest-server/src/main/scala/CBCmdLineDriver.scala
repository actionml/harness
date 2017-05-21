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
import com.actionml.router.admin.MongoAdministrator
import com.typesafe.scalalogging.LazyLogging
import scaldi.akka.AkkaInjectable
import scopt._

import scala.io.Source

case class CBCmdLineDriverConfig(
  modelOut: String = "", // db for model
  inputEvents: String = "data/xyxon-pio-data.json", // events readFile
  engineDefJSON: String = ""  // engine.json readFile
)

object CBCmdLineDriver extends App with AkkaInjectable with LazyLogging{

  override def main(args: Array[String]): Unit = {
    val parser = new OptionParser[CBCmdLineDriverConfig]("scopt") {
      head("scopt", "3.x")

      opt[String]('m', "model").action((x, c) =>
        c.copy(modelOut = x)).text("Model storage location, eventually from the Engine config")

      opt[String]('d', "dataset").action((x, c) =>
        c.copy(inputEvents = x)).text("Event dataset input location, eventually fome the Engine config")

      opt[String]('e', "engine").required().action((x, c) =>
        c.copy(engineDefJSON = x)).text("Engine config, JSON readFile now, but eventually from shared key/value store")

      help("help").text("prints this usage text")

      note("some notes.")

    }

    // parser.parse returns Option[C]
    parser.parse(args, CBCmdLineDriverConfig()) match {
      case Some(config) =>
        run(config)

      case None =>
      // arguments are bad, error message will have been displayed
    }
  }

  def run( config: CBCmdLineDriverConfig ): Unit = {
    // The pio-kappa rest-server should startup the administrator, which starts any persisted engines
    // then the Python CLI will control CRUD on Engines. We need to use factories that take json for
    // creating Engines so the Administrator and CLI is Engine independent
    // For now we assume CBEngines in the MongoAdministrator

    val admin = new MongoAdministrator().init()
    val engineJson = Source.fromFile(config.engineDefJSON).mkString

    admin.removeEngine("test_resource") // should remove and destroy an engine initialized at startup
    // so we can re-initialize in case of old data in the DB

    admin.addEngine(engineJson)
    val engine = admin.getEngine("test_resource")

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
      case Valid(result) ⇒ logger.trace(s"QueryResult: $result")
      case Invalid(error) ⇒ logger.error("Query error {}",error)
    }
  }
}
