package com.actionml.authserver.dal.mongo

import com.actionml.authserver.dal.UsersDao
import com.actionml.authserver.model.UserAccount
import org.mongodb.scala.model.Filters._
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}

class UsersDaoImpl(implicit inj: Injector) extends UsersDao with MongoSupport with Injectable {
  private val users = collection[UserAccount]("users")
  private implicit val ec = inject[ExecutionContext]

  override def find(id: String, passwordHash: String): Future[Option[UserAccount]] = {
    users.find(equal("id", id))
      .collect
      .toFuture
      .map(_.headOption)
  }
}

