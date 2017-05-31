package com.actionml.core.backup

import java.time.format.DateTimeFormatter

/**
  * TODO scaladoc
  */
abstract class Mirroring {
  protected val dirNamePattern: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy")
  protected val fileNamePattern: DateTimeFormatter = DateTimeFormatter.ofPattern("HH-mm-ss.SSS")

  def mirrorJson(json: String): Unit
}
