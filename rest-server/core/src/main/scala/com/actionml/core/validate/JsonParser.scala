package com.actionml.core.validate


trait JsonParser {

  import org.joda.time.DateTime
  import org.json4s.ext.JodaTimeSerializers
  import org.json4s.jackson.JsonMethods._
  import org.json4s.{DefaultFormats, Formats, MappingException}
  import org.json4s.ext.JodaTimeSerializers
  import org.json4s.jackson.JsonMethods._
  import org.json4s.{DefaultFormats, MappingException}
  import cats.data.Validated
  import cats.data.Validated.{Invalid, Valid}

  implicit val formats: Formats = DefaultFormats ++ JodaTimeSerializers.all //needed for json4s parsing


}
