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

package com.actionml.core.store

import com.actionml.core.store.DaoQuery.QueryCondition


case class DaoQuery(offset: Int = 0, limit: Int = Int.MaxValue, orderBy: Option[OrderBy] = None, filter: Seq[(String, QueryCondition)] = Seq.empty)

object DaoQuery {
  val empty = DaoQuery()


  sealed trait QueryCondition {
    def value: Any
  }
  case class GreaterOrEqualsTo(value: Any) extends QueryCondition
  case class GreaterThen(value: Any) extends QueryCondition
  case class LessOrEqualsTo(value: Any) extends QueryCondition
  case class LessThen(value: Any) extends QueryCondition
  case class Equals(value: Any) extends QueryCondition

  object syntax {

    implicit class DaoQueryOps(name: String) {
      def gte(v: Any): (String, QueryCondition) = {
        name -> GreaterOrEqualsTo(v)
      }
      def lte(v: Any): (String, QueryCondition) = {
        name -> LessOrEqualsTo(v)
      }
      def gt(v: Any): (String, QueryCondition) = {
        name -> GreaterThen(v)
      }
      def lt(v: Any): (String, QueryCondition) = {
        name -> LessThen(v)
      }
      def ===(v: Any): (String, QueryCondition) = {
        name -> Equals(v)
      }
    }
  }
}

object Ordering extends Enumeration {
  type Ordering = Value
  val asc = Value("asc")
  val desc = Value("desc")
  val default = desc
}

case class OrderBy(ordering: Ordering.Ordering, fieldNames: String*)

