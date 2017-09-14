package com.actionml.authserver.dal.mongo

import akka.event.LoggingAdapter
import com.actionml.authserver.dal.UsersDao
import com.actionml.authserver.model.UserAccount
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}

class UsersDaoImpl(implicit inj: Injector) extends UsersDao with MongoSupport with Injectable {
  private val users = collection[UserAccount]("users")
  private implicit val ec = inject[ExecutionContext]

  override def find(id: String)(implicit log: LoggingAdapter): Future[Option[UserAccount]] = {
    users.find(equal("id", id))
      .toFuture
      .recover { case e =>
        log.error(s"Can't find user with id $id", e)
        List.empty
      }.map(_.headOption)
  }

  override def find(id: String, secretHash: String)(implicit log: LoggingAdapter): Future[Option[UserAccount]] = {
    users.find(and(
      equal("id", id),
      equal("secretHash", secretHash)
    )).toFuture
      .recover { case e =>
        log.error(s"Can't find user with id $id and password hash $secretHash")
        List.empty
      }.map(_.headOption)
  }

  override def list(offset: Int, limit: Int)(implicit log: LoggingAdapter): Future[Iterable[UserAccount]] = {
    users.find()
      .skip(offset)
      .limit(limit)
      .toFuture
      .recover {
        case e: Exception =>
          log.error(s"Can't list users: ${e.getMessage}", e)
          List.empty
      }
  }

  override def update(user: UserAccount)(implicit log: LoggingAdapter): Future[Unit] = {
    users.insertOne(user)
      .toFuture
      .map(_ => ())
  }

  def delete(userId: String)(implicit log: LoggingAdapter): Future[Unit] = {
    users.deleteOne(equal("id", userId))
      .toFuture
      .map(_ => ())
  }
}

