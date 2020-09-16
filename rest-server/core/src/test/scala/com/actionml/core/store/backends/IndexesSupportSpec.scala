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
import com.actionml.core.store.Ordering._
import com.actionml.core.store.indexes.annotations.{CompoundIndex, SingleIndex}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{FlatSpec, Matchers}


@CompoundIndex(List("entityId" -> asc, "eventTime" -> desc))
case class EGClass(ni: String,
                   @SingleIndex(asc, isTtl = false) i: Int,
                   @SingleIndex(default, isTtl = true) ittl: String)
  extends Event with Serializable

class IndexesSupportSpec extends FlatSpec with Matchers {
  "getRequiredIndexesInfo" should "return correct indexes" in {
    val indexesSupport = new IndexesSupport with LazyLogging {}

    val requiredIndexes = List(
      "i" -> SingleIndex(asc, isTtl = false),
      "ittl" -> SingleIndex(desc, isTtl = true),
      "" -> CompoundIndex(List("eventTime" -> desc, "entityId" -> asc))
    )
    val actualIndexes = indexesSupport.getRequiredIndexesInfo[EGClass]

    actualIndexes.size should equal(requiredIndexes.size)
    actualIndexes.filter(_._2.isInstanceOf[SingleIndex]) should contain theSameElementsAs requiredIndexes.filter(_._2.isInstanceOf[SingleIndex])
    val requiredCompound = actualIndexes.flatMap {
      case (_, CompoundIndex(fields)) => fields
      case _ => List.empty
    }
    val expectedCompound = requiredIndexes.flatMap {
      case (_, CompoundIndex(fields)) => fields
      case _ => List.empty
    }
    requiredCompound should contain theSameElementsAs expectedCompound
  }
}
