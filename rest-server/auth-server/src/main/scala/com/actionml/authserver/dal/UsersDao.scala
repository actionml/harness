package com.actionml.authserver.dal

import com.actionml.authserver.model.UserAccount

import scala.concurrent.Future

trait UsersDao {
  def find(id: String, secretHash: String, clientId: String): Future[Option[UserAccount]]
  def update(user: UserAccount): Future[_]
}