package com.actionml.authserver.dal.mongo

import com.actionml.authserver.dal.PermissionDao
import com.actionml.authserver.model.Permission
import com.mongodb.client.model.Filters
import org.mongodb.scala.MongoCollection

import scala.concurrent.Future

class PermissionDaoImpl extends PermissionDao with MongoSupport {
  override def findByBearerToken(tokenHash: String): Future[Iterable[Permission]] = {
    val permissions: MongoCollection[Permission] = mongoDb.getCollection("permissions")
    permissions.find(Filters.eq("secretHash" -> tokenHash))
      .collect
      .head
  }
}

