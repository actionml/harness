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
import com.actionml.core.HIO
import com.actionml.core.backup.MirrorTypes
import com.actionml.core.backup.MirrorTypes.MirrorType
import com.fasterxml.jackson.databind.ObjectMapper
import com.typesafe.scalalogging.LazyLogging
import org.json4s
import org.json4s.JsonAST.JString
import org.json4s.ext.JodaTimeSerializers
import org.json4s.jackson.JsonMethods._
import org.json4s.{CustomSerializer, DateFormat, DefaultFormats, Extraction, Formats, JObject, JValue, MappingException, Reader, TypeInfo}
import zio.IO

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import scala.util.control.NonFatal


trait JsonSupport extends LazyLogging {

  implicit object DateReader extends Reader[Date] {
    override def read(value: json4s.JValue): Date = {
      dateFormats.dateFormat.parse(value.extract[String])
        .getOrElse(throw new RuntimeException(s"Can't parse date $value"))
    }
  }
  implicit object StringReader extends Reader[String] {
    override def read(value: json4s.JValue): String = enrichViaSystemEnv(value.extract[String])
  }
  implicit object JObjectReader extends Reader[JObject] {
    override def read(value: json4s.JValue): JObject = {
      value.extract[JObject]
    }
  }
  implicit val dateFormats: Formats = CustomFormats ++ JodaTimeSerializers.all + new StringSystemEnvSerializer + new MirrorTypesSerializer
  object CustomFormats extends DefaultFormats {
    override val dateFormat: DateFormat = new DateFormat {
      private val readFormat = DateTimeFormatter.ISO_DATE_TIME
      private val writeFormat = DateTimeFormatter.ISO_OFFSET_DATE_TIME

      override def parse(s: String): Option[Date] = {
        try {
          Option(Date.from(Instant.from(readFormat.parse(s))))
        } catch {
          case NonFatal(e) =>
            logger.error(s"Can't parse date $s", e)
            None
        }
      }

      override def format(d: Date): String = writeFormat.format(Instant.ofEpochMilli(d.getTime).atZone(timezone.toZoneId))

      override def timezone: TimeZone = TimeZone.getTimeZone("UTC")
    }
  }

  class StringSystemEnvSerializer extends CustomSerializer[String](format => (
    {
      case JString(s) => enrichViaSystemEnv(s)
    },
    {
      case s: String => JString(s)
    }
  ))

  class MirrorTypesSerializer extends CustomSerializer[MirrorType](format => (
    {
      case JString("local_fs") => MirrorTypes.localfs
      case JString(s) => MirrorTypes.withName(s)
    },
    {
      case m: MirrorType => JString(m.toString)
    }
  ))

  def parseAndValidate[T](
    json: String,
    errorMsg: String = "",
    transform: JValue => JValue = a => a)(implicit tag: TypeTag[T], ct: ClassTag[T], mf: Manifest[T]): Validated[ValidateError, T] = {

    try{
      Valid(transform(parse(json)).extract[T])
    } catch {
      case e: MappingException =>
        val msg = if (errorMsg.isEmpty) {
          tag.tpe match {
            case TypeRef(_, _, args) =>
              s"Error $args from JSON: $json"
            case _ => errorMsg
          }
        } else { errorMsg }
        logger.error(msg + s"$json", e)
        Invalid(ParseError(s"""{"comment":"${msg + json}"}"""))
    }
  }

  def parseAndValidateIO[T](json: String, errorMsg: String = "", transform: JValue => JValue = a => a)
                           (implicit tag: TypeTag[T], ct: ClassTag[T], mf: Manifest[T]): HIO[T] = {
    lazy val msg = if (errorMsg.isEmpty) {
      tag.tpe match {
        case TypeRef(t, s, args) =>
          s"Error converting to $mf from JSON:"
        case _ => "JSON parse error: "
      }
    } else { errorMsg }
    def handleError: Throwable => ValidateError = {
      case e: MappingException =>
        logger.error(s"$msg $json", e)
        ParseError(s"""{"comment":"$msg $json"}""")
      case NonFatal(e) =>
        logger.error(s"msg + $json", e)
        ValidRequestExecutionError(msg)
    }

    IO.effect(transform(parse(json)).extract[T])
      .mapError(handleError)
  }

  def prettify(jsonString: String): String = {
    val mapper = new ObjectMapper()
    try {
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

  def toJsonString[A <: AnyRef](a: A): String = org.json4s.native.Serialization.write(a)


  private val env = s"system.env.(.*)".r
  private def enrichViaSystemEnv: String => String = {
    case env(value) => sys.env.getOrElse(value, {
      logger.warn(s"No ENV VAR for $value")
      value
    })
    case s => s
  }
}
