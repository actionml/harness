package com.actionml.authserver.dal

import com.actionml.authserver.model.Permission

import scala.concurrent.Future

trait PermissionDaoComponent {

  trait PermissionDao {
    def findByBearerToken(token: String): Future[Iterable[Permission]]
  }

  def permissionDao: PermissionDao
}
