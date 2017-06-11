package com.actionml.core.backup

import java.io.{File, PrintWriter}

/**
  * TODO scaladoc
  */
object FSMirroring extends Mirroring {
  override def mirrorJson(engineId: String, json: String): Unit = {
    new File(engineId).mkdir()
    val pw = new PrintWriter(new File(s"${ engineId }/${ fileNamePattern.format(now) }.json"))
    try {
      pw.write(json)
    } finally {
      pw.close()
    }
  }
}
