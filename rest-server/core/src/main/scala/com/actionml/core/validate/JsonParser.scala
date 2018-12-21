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
import org.json4s.JValue
import org.json4s.ext.JodaTimeSerializers

import scala.reflect.ClassTag
import com.fasterxml.jackson.core.{JsonParseException, JsonProcessingException}
import com.fasterxml.jackson.databind.{JavaType, JsonMappingException, ObjectMapper, SerializationFeature}
import java.io.IOException

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import org.json4s
import org.json4s.jackson.Json
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, Formats, MappingException}

import scala.reflect.runtime.universe._


trait JsonParser extends LazyLogging {



  implicit val formats: Formats = DefaultFormats ++ JodaTimeSerializers.all

  def parseAndValidate[T : ClassTag](
    json: String,
    errorMsg: String = "",
    transform: JValue => JValue = a => a)(implicit tag: TypeTag[T]): Validated[ValidateError, T] = {

    try{
      Valid(transform(parse(json)).extract[T])
    } catch {
      case e: MappingException =>
        val msg = if (errorMsg.isEmpty) {
          tag.tpe match {
            case TypeRef(_, _, args) =>
            s"Error $args from JSON: $json"
          }
        } else { errorMsg }
        logger.error(msg + s"$json", e)
        Invalid(ParseError(jsonComment(msg + s"$json")))
    }
  }

  def prettify(jsonString: String): String = {
    try {
      json4s.jackson.prettyJson(parse(jsonString))
    } catch {
      case e@(_ :IOException | _ :JsonParseException | _ :JsonMappingException) =>
        logger.error(s"Can't parse $jsonString", e)
        jsonComment(s"Bad Json in prettify: $jsonString")
    }
  }

  def jsonComment(comment: String): String = {
    s"""
       |{
       |    "comment": "$comment"
       |}
    """.stripMargin
  }

  def jsonList(jsonStrings: Seq[String]): String = {
    // todo: create then pretty print instead
    "[" + jsonStrings.mkString(",") + "]"
  }


}
