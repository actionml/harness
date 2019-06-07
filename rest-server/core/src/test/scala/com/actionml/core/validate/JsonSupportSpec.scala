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

package com.actionml.core.validate

import cats.data.Validated.Valid
import org.scalatest.{FlatSpec, Matchers}

class JsonSupportSpec extends FlatSpec with Matchers {
  setEnv("FOO", "BAR")
  setEnv("SPARK_JOB_SERVER_URL", "BAR")

  "parseAndValidate" should "replace env links with environment variables values" in {
    new JsonSupport{}.parseAndValidate[A](
      """{
        |"env": "system.env.FOO"
        |}""".stripMargin
    ) should equal(Valid(A("BAR")))
  }

  private case class A(env: String)

  private def setEnv(key: String, value: String) = {
    val field = System.getenv().getClass.getDeclaredField("m")
    field.setAccessible(true)
    val map = field.get(System.getenv()).asInstanceOf[java.util.Map[java.lang.String, java.lang.String]]
    map.put(key, value)
  }

}
