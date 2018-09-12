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

trait SearchSupport[T] {
  def createSearchClient(engineId: String): SearchClient[T]
}

case class Matcher(name: String, values: Seq[String], boost: Option[Float] = None)
case class SearchQuery(
  sortBy: String = "popRank", // todo: make it optional and changeable
  should: Map[String, Seq[Matcher]] = Map.empty,
  must: Map[String, Seq[Matcher]] = Map.empty,
  mustNot: Map[String, Seq[Matcher]] = Map.empty,
  size: Int = 20,
  from: Int = 0 // todo: technically should be optional and changeable, but no sure sending 0 is bad in any way
)

case class Hit(id: String, score: Float)

trait SearchClient[T] {
  def close(): Unit
  def createIndex(
    indexType: String,
    fieldNames: List[String],
    typeMappings: Map[String, (String, Boolean)] = Map.empty,
    refresh: Boolean = false): Boolean
  def deleteIndex(refresh: Boolean = false): Boolean
  def search(query: SearchQuery): Seq[T]
}
