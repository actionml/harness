package com.actionml.core.backup

import java.io.{File, FileWriter, PrintWriter}

/**
  * Mirroring implementation for local FS.
  */

// TODO: not using injection so need a trait
//object FSMirroring extends Mirroring {
trait FSMirroring extends Mirroring {

  val f = new File(mirrorContainer)
  if( f.exists() && f.isDirectory() && config.getString("mirror.type").nonEmpty) {
    logger.info(s"Morroring raw un-validated events to ${mirrorContainer}")
  } else if(config.getString("mirror.type").nonEmpty){
    logger.error(s"Mirror location: ${mirrorContainer} not configured to accept event mirroring.")
  } else {
    logger.warn(s"Mirroring location is set but type is not configured, no mirroring with be done.")
  }



  // java.io.IOException could be thrown here in case of system errors
  override def mirrorJson(engineId: String, json: String): Unit = {
    if(mirrorType == Mirroring.localfs){
      try {
        val resourceCollection = new File(containerName(engineId))
        //logger.info(s"${containerName(engineId)} exists: ${resourceCollection.exists()}")
        if( !resourceCollection.exists()) new File(s"${containerName(engineId)}").mkdir()
        val pw = new PrintWriter(new FileWriter(s"${containerName(engineId)}/$batchName.json", true))
        try {
          pw.write(json)
        } finally {
          pw.close()
        }
      }

    } else {
      logger.warn("Local filesystem mirroring called, but not configured.")
    }
  }
}
