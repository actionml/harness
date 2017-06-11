package com.actionml.core.backup

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}

/**
  * Basic trait for JSON backing up.
  *
  */
trait Mirroring {
  def mirrorJson(engineId: String, json: String): Unit

  /**
    * File names are always formatted with "ddMMyy-HHmmss.SSS" template.
    * @return timestamp-based file name
    */
  protected def fileName: String =
    DateTimeFormatter.ofPattern("ddMMyy-HHmmss.SSS").format(LocalDateTime.now(ZoneId.of("UTC")))

  /**
    * Directory name is Engine ID for all the implementations
    * @param engineId Engine ID
    * @return directory name
    */
  protected def directoryName(engineId: String): String = engineId
}
