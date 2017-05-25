package com.actionml.core.backup

import java.io.{File, PrintWriter}
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}

import scala.util.Try

trait Mirroring {
  private val dirNamePattern = DateTimeFormatter.ofPattern("d-MMM-yyyy")
  private val fileNamePattern = DateTimeFormatter.ofPattern("HH-mm-ss.SSS")

  def mirrorJson(json: String): Unit = {
    val now = LocalDateTime.now(ZoneId.of("UTC"))
    val directory = new File(dirNamePattern.format(now))
    directory.mkdir()
    val pw = new PrintWriter(new File(s"${ directory.getName }/${ fileNamePattern.format(now) }.json"))
    Try { pw.write(json) }
    pw.close()
  }
}
