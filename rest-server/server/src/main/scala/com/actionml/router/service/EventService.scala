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

import cats.data.Validated
import cats.data.Validated.Invalid
import com.actionml.admin.Administrator
import com.actionml.core.model.Response
import com.actionml.core.validate.{JsonSupport, NotImplemented, ValidateError, WrongParams}

import scala.concurrent.Future

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 28.01.17 14:49
  */

trait EventService {
  def getEvent(engineId: String, event: String): Future[Validated[ValidateError, Response]]
  def createEvent(engineId: String, event: String): Future[Validated[ValidateError, Response]]
}

class EventServiceImpl(admin: Administrator) extends EventService with JsonSupport {
  override def getEvent(engineId: String, event: String): Future[Validated[ValidateError, Response]] =
    Future.successful(Invalid(NotImplemented()))

  override def createEvent(engineId: String, event: String): Future[Validated[ValidateError, Response]] = {
    admin.getEngine(engineId).fold[Future[Validated[ValidateError, Response]]](Future.successful(Invalid(WrongParams(jsonComment(s"Engine for id=$engineId not found"))))) { engine =>
      engine.inputAsync(event)
    }
  }
}
