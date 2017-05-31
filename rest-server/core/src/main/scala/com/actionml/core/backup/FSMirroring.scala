package com.actionml.core.backup

import java.io.{File, PrintWriter}
import java.time.{LocalDateTime, ZoneId}

import scala.util.Try

/**
  * TODO scaladoc
  */
object FSMirroring extends Mirroring {
  override def mirrorJson(json: String): Unit = {
    val now = LocalDateTime.now(ZoneId.of("UTC"))
    val directory = new File(dirNamePattern.format(now))
    directory.mkdir()
    val pw = new PrintWriter(new File(s"${ directory.getName }/${ fileNamePattern.format(now) }.json"))
    Try { pw.write(json) }
    pw.close()
  }
}
