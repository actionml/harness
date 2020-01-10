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

package com.actionml.core.search

import com.actionml.core.search.Filter.Conditions.Condition
import com.actionml.core.search.Filter.Types.Type

import scala.concurrent.Future

trait SearchSupport[R, D] {
  def createSearchClient(engineId: String): SearchClient[R, D]
}

case class Matcher(name: String, values: Seq[String], boost: Option[Float] = None)

/**
  * com.actionml.core.search.syntax contains a syntax for Filters, so instead of manual instantiation
  * {{{
  *   Filter(Types.range, "name", Conditions.eq, value)
  * }}}
  * one can use
  * {{{
  * import com.actionml.core.search.syntax._
  * name eq value
  * }}}
  *
  * @param `type` - type of the filter (range | term)
  * @param name - name of the field
  * @param condition - eq|gt|gte|lt|lte
  * @param value - any value of this field
  */
case class Filter(`type`: Type, name: String, condition: Condition, value: Any)

object Filter {
  object Types extends Enumeration {
    type Type = Value

    val range = Value("range")
    val term = Value("term")
  }

  object Conditions extends Enumeration {
    type Condition = Value

    val eq = Value("eq")
    val gte = Value("gte")
    val gt = Value("gt")
    val lte = Value("lte")
    val lt = Value("lt")
  }
}

case class SearchQuery(
  sortBy: String = "popRank", // todo: make it optional and changeable
  should: Seq[Matcher] = Seq.empty,
  must: Seq[Matcher] = Seq.empty,
  mustNot: Seq[Matcher] = Seq.empty,
  filters: Seq[Filter] = Seq.empty,
  size: Int = 20,
  from: Int = 0 // todo: technically should be optional and changeable, but not sure sending 0 is bad in any way
)

case class Hit(id: String, score: Float)

/*
  R is the type of search result, D - type of Document
 */
trait SearchClient[R, D] {
  def close(): Unit
  def createIndex(
    fieldNames: List[String],
    typeMappings: Map[String, (String, Boolean)] = Map.empty,
    refresh: Boolean = false): Boolean
  def saveOneById(id: String, doc: D): Boolean
  def deleteIndex(refresh: Boolean = false): Boolean
  def search(query: SearchQuery): Future[Seq[R]]
  def findDocById(id: String): D
}
