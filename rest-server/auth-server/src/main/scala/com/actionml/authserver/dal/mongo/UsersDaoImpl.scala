package com.actionml.authserver.dal.mongo

import com.actionml.authserver.dal.UsersDao
import com.actionml.authserver.exceptions.UserNotFoundException
import com.actionml.authserver.model.UserAccount
import com.typesafe.scalalogging.LazyLogging
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}

class UsersDaoImpl(implicit inj: Injector) extends UsersDao with MongoSupport with Injectable with LazyLogging {
  private val users = collection[UserAccount]("users")
  private implicit val ec = inject[ExecutionContext]

  override def find(id: String): Future[Option[UserAccount]] = {
    users.find(equal("id", id))
      .toFuture
      .recover { case e =>
        logger.error(s"Can't find user with id $id", e)
        List.empty
      }.map(_.headOption)
  }

  override def find(id: String, secretHash: String): Future[Option[UserAccount]] = {
    users.find(and(
      equal("id", id),
      equal("secretHash", secretHash)
    )).toFuture
      .recover { case e =>
        logger.error(s"Can't find user with id $id and password hash $secretHash", e)
        List.empty
      }.map(_.headOption)
  }

  override def list(offset: Int, limit: Int): Future[Iterable[UserAccount]] = {
    users.find()
      .skip(offset)
      .limit(limit)
      .toFuture
      .recover {
        case e: Exception =>
          logger.error(s"Can't list users: ${e.getMessage}", e)
          List.empty
      }
  }

  override def update(user: UserAccount): Future[Unit] = {
    users.insertOne(user)
      .toFuture
      .map(_ => ())
  }

  def delete(userId: String): Future[Unit] = {
    users.deleteOne(equal("id", userId))
      .toFuture
      .map(result => if (result.getDeletedCount == 1) () else throw UserNotFoundException)
  }
}

