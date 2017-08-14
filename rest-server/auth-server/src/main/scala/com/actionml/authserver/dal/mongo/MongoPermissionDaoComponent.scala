package com.actionml.authserver.dal.mongo

import com.actionml.core.ExecutionContextComponent
import com.actionml.authserver.dal.PermissionDaoComponent
import com.actionml.authserver.model.Permission
import com.mongodb.client.model.Filters
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.codecs.Macros._

import scala.concurrent.Future

trait MongoPermissionDaoComponent extends PermissionDaoComponent {
  this: ExecutionContextComponent =>

  override def permissionDao: PermissionDao = new PermissionDaoImpl

  class PermissionDaoImpl extends PermissionDao with MongoSupport {
    override def findByBearerToken(tokenHash: String): Future[Iterable[Permission]] = {
      val permissions: MongoCollection[Permission] = mongoDb.getCollection("permissions")
      permissions.find(Filters.eq("secretHash" -> tokenHash))
        .collect
        .head
    }
  }

  private val codecRegistry = fromRegistries(fromProviders(classOf[Permission]), DEFAULT_CODEC_REGISTRY )
}
