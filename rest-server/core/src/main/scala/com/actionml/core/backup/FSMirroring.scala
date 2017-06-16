package com.actionml.core.backup

import java.io.{File, PrintWriter}

/**
  * Mirroring implementation for FS.
  */
object FSMirroring extends Mirroring {

  // java.io.IOException could be thrown here in case of system errors
  override def mirrorJson(engineId: String, json: String): Unit = {
    //TODO set root directory
    new File(engineId).mkdir()
    val pw = new PrintWriter(new File(s"${ directoryName(engineId) }/$fileName.json"))
    try {
      pw.write(json)
    } finally {
      pw.close()
    }
  }
}
