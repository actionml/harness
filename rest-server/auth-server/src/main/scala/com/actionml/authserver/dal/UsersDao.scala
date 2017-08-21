package com.actionml.authserver.dal

import com.actionml.authserver.model.UserAccount

import scala.concurrent.Future

trait UsersDao {
  def find(username: String, passwordHash: String): Future[Option[UserAccount]]
}