package com.actionml.core.backup

import java.time.{LocalDateTime, ZoneId}
import java.time.format.DateTimeFormatter

/**
  * TODO scaladoc
  */
abstract class Mirroring {
  protected val fileNamePattern: DateTimeFormatter = DateTimeFormatter.ofPattern("ddMMyy-HHmmss.SSS")

  def mirrorJson(engineId: String, json: String): Unit

  protected def now: LocalDateTime = LocalDateTime.now(ZoneId.of("UTC"))
}
