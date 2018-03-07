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

package com.actionml.core.validate

import com.typesafe.scalalogging.LazyLogging

import scala.reflect.ClassTag


trait JsonParser extends LazyLogging{

  import org.joda.time.DateTime
  import org.json4s.ext.JodaTimeSerializers
  import org.json4s.jackson.JsonMethods._
  import org.json4s.{DefaultFormats, Formats, MappingException}
  import org.json4s.ext.JodaTimeSerializers
  import org.json4s.jackson.JsonMethods._
  import org.json4s.{DefaultFormats, MappingException}
  import cats.data.Validated
  import cats.data.Validated.{Invalid, Valid}
  import scala.reflect.runtime.universe._


  implicit val formats: Formats = DefaultFormats ++ JodaTimeSerializers.all //needed for json4s parsing

  def parseAndValidate[T : ClassTag](
    json: String,
    errorMsg: String = "")(implicit tag: TypeTag[T]): Validated[ValidateError, T] = {

    try {
      Valid(parse(json).extract[T])
    } catch {
      case e: MappingException =>
        val msg = if (errorMsg.isEmpty) {
          tag.tpe match {
            case TypeRef(_, _, args) =>
            s"Error $args from JSON: $json"
          }
        } else { errorMsg }
        logger.error(msg + s"${json}", e)
        Invalid(ParseError(msg + s"${json}"))
    }
  }


}
