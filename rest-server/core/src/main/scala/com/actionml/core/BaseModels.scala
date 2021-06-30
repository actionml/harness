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

package com.actionml.core.model

import com.actionml.core.backup.MirrorTypes.MirrorType
import org.json4s.{Formats, JArray}

import scala.language.implicitConversions

/* todo: we should come up with a better way of composing or mixing case classes so they can be more
* easily extended in child classes while allowing the child class to parse (json4s) the parent
* case class from JSON. For instance EngineParams might be used by Engine, but some child of
* EngineParams would be used in a chile Engine class.
*
* See 2 methods to do this here: https://stackoverflow.com/questions/12705309/scala-case-class-inheritance
*/

case class User(
    _id: String,
    properties: Map[String, String]) {
  //def toSeq = properties.split("%").toSeq // in case users have arrays of values for a property, salat can't handle
  // Todo: this is an old hack for Salat, should be removed?
  def propsToMapOfSeq = properties.map { case(propId, propString) =>
    propId -> propString.split("%").toSeq
  }
}


object User {
  // convert the Map[String, Seq[String]] to Map[String, String] by encoding the propery values in a single string
  // Todo: this is an old hack for Salat, should be removed?
  def propsToMapString(props: Map[String, Seq[String]]): Map[String, String] = {
    props.filter { (t) =>
      t._2.size != 0 && t._2.head != ""
    }.map { case (propId, propSeq) =>
      propId -> propSeq.mkString("%")
    }
  }
}


/** Contains params required by all engines */
case class GenericEngineParams(
    engineId: String, // required, resourceId for engine
    engineFactory: String,
    mirrorType: Option[MirrorType] = None,
    mirrorContainer: Option[String] = None,
    sharedDBName: Option[String] = None,
    modelContainer: Option[String] = None,
    algorithm: Option[String] = None)
  extends Response with EngineParams


// allows us to look at what kind of specialized event to create
case class GenericEvent (
    //eventId: String, // not used in Harness, but allowed for PIO compatibility
    event: String,
    entityType: String,
    entityId: String,
    targetEntityId: Option[String] = None,
    properties: Option[Map[String, Any]] = None,
    eventTime: String, // ISO8601 date
    creationTime: String) // ISO8601 date
  extends Event

/** Used only for illustration since queries have no required part */
case class GenericQuery() extends Query {
  def toJson =
    s"""
       |{
       |    "dummyQuery": "query"
       |}
     """.stripMargin
}

/** Used only for illustration since query results have no required part */
case class GenericQueryResult() extends Response with QueryResult{
  def toJson =
    s"""
       |{
       |    "dummyQueryResult": "result"
       |}
     """.stripMargin
}

/** Extend these with engine specific case classes */
trait AlgorithmParams
trait AlgorithmQuery
trait QueryResult
trait Event
trait EngineParams
trait Query
trait Response
object Response {
  import org.json4s.jackson.Serialization
  import org.json4s.{Extraction, JValue, NoTypeHints}
  implicit val formats: Formats = Serialization.formats(NoTypeHints)
  implicit def responseToJValue[T <: Response]: T => JValue = Extraction.decompose _
  implicit def responseListToJArray[T <: List[Response]]: T => JArray = l => JArray(l.map(Extraction.decompose _))
}
case class Comment(comment: String) extends Response
