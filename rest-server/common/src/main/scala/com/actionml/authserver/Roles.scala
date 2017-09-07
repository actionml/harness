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

package com.actionml.authserver

object Roles {

  object engine {
    val create = "engine_create"
    val modify = "engine_modify"
    val read = "engine_read"
  }

  object event {
    val create = "event_create"
    val modify = "event_modify"
    val read = "event_read"
  }

  object query {
    val create = "query_create"
    val modify = "query_modify"
    val read = "query_read"
  }

  object user {
    val create = "user_create"
    val permissions = "user_permissions"
  }
}
