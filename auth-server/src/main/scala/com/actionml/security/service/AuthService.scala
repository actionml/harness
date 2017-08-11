package com.actionml.security.service

import java.time.LocalDateTime

import com.actionml.core.ExecutionContextComponent
import com.actionml.security.dal.{AccessTokenDaoComponent, PermissionDaoComponent}
import com.actionml.security.model.{AccessToken, Permission}

import scala.concurrent.Future

trait AuthServiceComponent {

  trait AuthService {
    def authenticate(bearerToken: String): Future[AccessToken]
    def authorize(accessToken: String, role: String, resourceId: String): Future[Boolean]
  }

  def authService: AuthService
}

trait AuthServiceComponentImpl extends AuthServiceComponent {
  this: AccessTokenDaoComponent
    with ExecutionContextComponent
    with PermissionDaoComponent =>

  override def authService: AuthService = new AuthServiceImpl

  class AuthServiceImpl extends AuthService {
    override def authenticate(bearerToken: String): Future[AccessToken] = {
      permissionDao.findByBearerToken(bearerToken)
      ???
    }

    override def authorize(accessToken: String, roleId: String, resourceId: String): Future[Boolean] = {
      accessTokenDao.findByAccessToken(accessToken)
        .collect {
          case AccessToken(_, permissions, createdAt) =>
            if (createdAt.isBefore(LocalDateTime.now)) throw new RuntimeException("Token Expired")
            else permissions.exists { permission =>
              permission.roleId == roleId &&
                (permission.resourceId == "*" || permission.resourceId == resourceId)
            }
          case _ => false
        }
    }
  }
}
