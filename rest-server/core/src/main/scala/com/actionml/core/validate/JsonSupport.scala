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

import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.{Date, TimeZone}

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.fasterxml.jackson.databind.ObjectMapper
import com.typesafe.scalalogging.LazyLogging
import org.json4s
import org.json4s.ext.JodaTimeSerializers
import org.json4s.jackson.JsonMethods._
import org.json4s.{DateFormat, DefaultFormats, Formats, JObject, JValue, MappingException, Reader}

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._


trait JsonSupport extends LazyLogging {

  implicit object DateReader extends Reader[Date] {
    override def read(value: json4s.JValue): Date = {
      dateFormats.dateFormat.parse(value.extract[String])
        .getOrElse(throw new RuntimeException(s"Can't parse date $value"))
    }
  }
  implicit object StringReader extends Reader[String] {
    override def read(value: json4s.JValue): String = value.extract[String]
  }
  implicit object JObjectReader extends Reader[JObject] {
    override def read(value: json4s.JValue): JObject = {
      value.extract[JObject]
    }
  }
  implicit val dateFormats: Formats = CustomFormats ++ JodaTimeSerializers.all
  object CustomFormats extends DefaultFormats {
    override val dateFormat: DateFormat = new DateFormat {
      private val readFormat = DateTimeFormatter.ISO_DATE_TIME
      private val writeFormat = DateTimeFormatter.ISO_OFFSET_DATE_TIME

      override def parse(s: String): Option[Date] = {
        try {
          Option(Date.from(Instant.from(readFormat.parse(s))))
        } catch {
          case e: Exception =>
            logger.error(s"Can't parse date $s", e)
            None
        }
      }

      override def format(d: Date): String = writeFormat.format(Instant.ofEpochMilli(d.getTime).atZone(timezone.toZoneId))

      override def timezone: TimeZone = TimeZone.getDefault
    }
  }

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
        Invalid(ParseError(s"""{"comment":"${msg + json}"}"""))
    }
  }

  def prettify(jsonString: String): String = {
    val mapper = new ObjectMapper()
    try {
      //val jsonObject = mapper.readValue(jsonString, Object.class)
      //val jsonObject = mapper.readValue(jsonString, Class[Object])
      //val jsonObject = mapper.readValue(jsonString, JavaType)
      //mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject)

      val json = mapper.readValue(jsonString, classOf[Any])
      mapper.writerWithDefaultPrettyPrinter.writeValueAsString(json)
    } catch {
      case e: IOException =>
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
    "[\n    " + jsonStrings.mkString(",\n    ") + "\n]"
  }


}
