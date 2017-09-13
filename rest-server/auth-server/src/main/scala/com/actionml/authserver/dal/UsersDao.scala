package com.actionml.authserver.dal

import akka.event.LoggingAdapter
import com.actionml.authserver.model.UserAccount

import scala.concurrent.Future

trait UsersDao {
  def find(id: String)(implicit log: LoggingAdapter): Future[Option[UserAccount]]
  def find(id: String, secretHash: String)(implicit log: LoggingAdapter): Future[Option[UserAccount]]
  def list(offset: Int, limit: Int)(implicit log: LoggingAdapter): Future[Iterable[UserAccount]]
  def update(user: UserAccount)(implicit log: LoggingAdapter): Future[Unit]
}