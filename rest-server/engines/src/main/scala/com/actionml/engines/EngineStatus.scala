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

package com.actionml.engines

import com.actionml.core.jobs.JobDescription
import com.actionml.core.model.GenericEngineParams
import com.actionml.engines.cb.CBAlgoParams
import com.actionml.engines.navhinting.NHAlgoParams
import com.actionml.engines.ur.UREngine.UREngineParams
import com.actionml.engines.urnavhinting.URNavHintingEngine.URNavHintingEngineParams


import io.circe._
import io.circe.generic.semiauto._
sealed trait Status

object Status {
  import io.circe._
  import io.circe.generic.semiauto._
  private implicit val defaultEngineStatusEncoder: Encoder[DefaultEngineStatus] = deriveEncoder[DefaultEngineStatus]
  private implicit val urEngineParamsEncoder = deriveEncoder[UREngineParams]
  private implicit val jobDescriptionEncoder = deriveEncoder[JobDescription]
  private implicit val urStatusEncoder = deriveEncoder[UREngineStatus]
  private implicit val urNavHintingEngineParamsEncoder = deriveEncoder[URNavHintingEngineParams]
  private implicit val urNavHintingStatusEncoder = deriveEncoder[URNavHintingEngineStatus]
  private implicit val gepe = deriveEncoder[GenericEngineParams]
  private implicit val cape = deriveEncoder[CBAlgoParams]
  private implicit val cbStatusEncoder = deriveEncoder[CBStatus]
  private implicit val nhap = deriveEncoder[NHAlgoParams]
  private implicit val navHintingStatusEncoder = deriveEncoder[NavHintingStatus]
  private implicit val scaffoldStatusEncoder = deriveEncoder[ScaffoldStatus]
  private implicit val failedEngineStatusEncoder = deriveEncoder[FailedEngineStatus]
}

case class DefaultEngineStatus(engineId: String, comment: String) extends Status

case class CBStatus(description: String = "Contextual Bandit Algorithm",
                    engineType: String = "Backed by the Vowpal Wabbit compute engine.",
                    engineParams: GenericEngineParams,
                    algorithmParams: CBAlgoParams,
                    activeGroups: Int) extends Status

case class NavHintingStatus(
    description: String = "Navigation Hinting Algorithm",
    engineType: String = "Simple analytical discovery of likely conversion paths",
    engineParams: GenericEngineParams,
    algorithmParams: NHAlgoParams)
  extends Status

case class ScaffoldStatus(params: GenericEngineParams) extends Status

case class UREngineStatus(engineParams: UREngineParams, jobStatuses: Map[String, JobDescription]) extends Status

case class URNavHintingEngineStatus(params: URNavHintingEngineParams, jobStatuses: Map[String, JobDescription]) extends Status

case class FailedEngineStatus(error: String) extends Status


