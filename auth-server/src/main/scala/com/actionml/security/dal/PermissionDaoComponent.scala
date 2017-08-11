package com.actionml.security.dal

import com.actionml.security.model.Permission

import scala.concurrent.Future

trait PermissionDaoComponent {

  trait PermissionDao {
    def findByBearerToken(token: String): Future[Iterable[Permission]]
  }

  def permissionDao: PermissionDao
}
