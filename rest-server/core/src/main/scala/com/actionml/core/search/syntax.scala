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

import com.actionml.core.search.FilterClause.Conditions
import com.actionml.core.search.FilterClause.Types._

object syntax {

  implicit class SearchFilterOps(name: String) {
    def ===(v: Any): FilterClause = {
      FilterClause(term, name, Conditions.eq, v.toString)
    }

    def gt(v: Any): FilterClause = {
      FilterClause(range, name, Conditions.gt, v.toString)
    }

    def gte(v: Any): FilterClause = {
      FilterClause(range, name, Conditions.gte, v.toString)
    }

    def lt(v: Any): FilterClause = {
      FilterClause(range, name, Conditions.lt, v.toString)
    }

    def lte(v: Any): FilterClause = {
      FilterClause(range, name, Conditions.lte, v.toString)
    }
  }
}
