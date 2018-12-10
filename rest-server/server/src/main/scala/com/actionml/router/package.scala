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

package com.actionml

import java.time.OffsetDateTime

import akka.actor.{Actor, ActorLogging}
import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import cats.syntax.either._
import com.actionml.core.utils.DateTimeUtil
import scaldi.akka.AkkaInjectable

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 29.01.17 16:28
  */
package object router {

  trait ActorInjectable extends Actor with ActorLogging with AkkaInjectable

  implicit val dateTimeEncoder: Encoder[OffsetDateTime] = Encoder.instance(a => a.toString().asJson)
  implicit val dateTimeDecoder: Decoder[OffsetDateTime] = Decoder.instance(a => a.as[String].map(DateTimeUtil.parseOffsetDateTime))

}
