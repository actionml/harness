package com.actionml.admin

import com.actionml.core.template.EngineParams

case class RequiredEngineParams(
  engineId: String, // required, resourceId for engine
  engineFactory: String
) extends EngineParams
