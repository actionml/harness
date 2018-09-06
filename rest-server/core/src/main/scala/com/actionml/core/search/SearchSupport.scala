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

import org.json4s.JValue

import scala.concurrent.Future

trait SearchSupport[T] {
  def createSearchClient(hosts: String*): SearchClient[T]
}

trait SearchClient[T] {
  def close: Unit
  def createIndex(
    indexName: String,
    indexType: String,
    fieldNames: List[String],
    typeMappings: Map[String, (String, Boolean)] = Map.empty,
    refresh: Boolean = false): Boolean
  def deleteIndex(indexName: String, refresh: Boolean = false): Boolean
  def refreshIndex(indexName: String): Unit
  def search(query: String, indexName: String): Option[T]
}
