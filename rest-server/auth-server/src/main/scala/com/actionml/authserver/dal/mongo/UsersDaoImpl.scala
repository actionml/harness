package com.actionml.authserver.dal.mongo

import com.actionml.authserver.dal.UsersDao
import com.actionml.authserver.model.UserAccount
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}

class UsersDaoImpl(implicit inj: Injector) extends UsersDao with MongoSupport with Injectable {
  private val users = collection[UserAccount]("users")
  private implicit val ec = inject[ExecutionContext]

  override def find(id: String, secretHash: String, clientId: String): Future[Option[UserAccount]] = {
    users.find(and(
      equal("id", id),
//      equal("secretHash", secretHash),
      equal("clientId", clientId)
    )).toFuture
      .recover {
        case e => e.printStackTrace(); List.empty
      }.map(_.headOption)
  }

  override def update(user: UserAccount): Future[_] = {
    users.insertOne(user)
      .toFuture
  }
}

