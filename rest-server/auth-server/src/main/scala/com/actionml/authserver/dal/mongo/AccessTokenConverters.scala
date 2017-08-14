package com.actionml.authserver.dal.mongo

import java.time.LocalDateTime

import com.actionml.authserver.model.{AccessToken, Permission}
import org.bson.BsonString
import org.mongodb.scala.Document
import org.mongodb.scala.bson.BsonArray

import scala.collection.JavaConverters._

trait AccessTokenConverters {

  def asDocument(accessToken: AccessToken): Document = {
    Document(
      "accessToken" -> accessToken.accessToken,
      "permissions" -> BsonArray(accessToken.permissions.map(permissionAsDocument.andThen(_.toBsonDocument))),
      "createdAt" -> accessToken.createdAt.toString
    )
  }

  def toAccessToken(doc: Document): AccessToken = {
    for {
      accessToken <- doc.get[BsonString]("accessToken").map(_.getValue)
      permissions <- doc.get[BsonArray]("permissions").map(_.getValues.asScala.map(bv => toPermission(bv.asDocument())))
      createdAt <- doc.get[BsonString]("createdAt").map(t => LocalDateTime.parse(t.getValue))
    } yield AccessToken(accessToken, permissions, createdAt)
  }.getOrElse(throw new RuntimeException("Unsupported data format"))


  private def toPermission(doc: Document): Permission = {
    for {
      clientId <- doc.get[BsonString]("clientId").map(_.getValue)
      role <- doc.get[BsonString]("roleId").map(_.getValue)
      resourceId <- doc.get[BsonString]("resourceId").map(_.getValue)
    } yield Permission(clientId, role, resourceId)
  }.getOrElse(throw new RuntimeException("Unsupported data format"))

  private def permissionAsDocument: Permission => Document = p => {
    Document(
      "clientId" -> p.clientId,
      "roleId" -> p.roleId,
      "resourceId" -> p.resourceId
    )
  }
}
