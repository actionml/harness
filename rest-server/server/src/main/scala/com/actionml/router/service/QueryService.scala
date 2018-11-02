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

package com.actionml.router.service

import cats.data.Validated.Invalid
import com.actionml.admin.Administrator
import com.actionml.core.engine.Engine
import com.actionml.core.validate.{JsonParser, WrongParams}
import com.actionml.router.ActorInjectable
import io.circe.syntax._
import scaldi.Injector

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 25.02.17 11:48
  */
trait QueryService extends ActorInjectable

class QueryServiceImpl(implicit inj: Injector) extends QueryService with JsonParser {

  private val admin = inject[Administrator]('Administrator)

  override def receive: Receive = {
    case GetPrediction(engineId, query) ⇒
      log.debug("Get prediction, {}, {}", engineId, query)
      admin.getEngine(engineId) match {
        case Some(engine) ⇒ sender() ! engine.query(query).map(_.asJson)
        case None ⇒ sender() ! Invalid(WrongParams(jsonComment(s"Engine for id=$engineId not found")))
      }

  }
}

sealed trait QueryAction
case class GetPrediction(engineId: String, query: String) extends QueryAction
