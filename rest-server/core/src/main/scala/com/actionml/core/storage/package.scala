package com.actionml.core

import com.mongodb.casbah.Imports._

package object storage {
  val allCollectionObjects = MongoDBObject("_id" -> MongoDBObject("$exists" -> true))
}
