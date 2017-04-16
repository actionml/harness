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

    try{
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
