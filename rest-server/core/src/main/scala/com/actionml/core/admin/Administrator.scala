/*
 * Copyright ActionML, LLC under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
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

package com.actionml.core.admin

import cats.data.Validated
import com.actionml.core.storage.Store
import com.actionml.core.template.Engine
import com.actionml.core.validate.ValidateError

/** Handles commands or Rest requests that are system-wide, not the concern of a single Engine */
abstract class Administrator[S <: Store](metaStore: S) {

  def add(engine: Engine): Validated[ValidateError, Boolean]
  def remove(engine: Engine): Validated[ValidateError, Boolean]

}
