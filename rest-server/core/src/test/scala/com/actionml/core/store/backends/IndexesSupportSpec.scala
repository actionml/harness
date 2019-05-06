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

package com.actionml.core.store.backends

import com.actionml.core.model.Event
import com.actionml.core.store
import com.actionml.core.store.indexes.annotations.Indexed
import org.scalatest.{FlatSpec, Matchers}


case class EGClass(ni: String,
                   @Indexed(store.Ordering.asc, isTtl = false) i: Int,
                   @Indexed(store.Ordering.default, isTtl = true) ittl: String)
  extends Event with Serializable

class IndexesSupportSpec extends FlatSpec with Matchers {
  "getRequiredIndexesInfo" should "return correct indexes" in {
    val indexesSupport = new IndexesSupport {}
    val egIndexes = List(
      "i" -> Indexed(store.Ordering.asc, isTtl = false),
      "ittl" -> Indexed(store.Ordering.desc, isTtl = true)
    )

    indexesSupport.getRequiredIndexesInfo[EGClass] should contain theSameElementsAs egIndexes
  }
}
