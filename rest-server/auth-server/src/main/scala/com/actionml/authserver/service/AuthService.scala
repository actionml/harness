package com.actionml.authserver.service

import java.time.LocalDateTime

import com.actionml.core.ExecutionContextComponent
import com.actionml.authserver.dal.{AccessTokenDaoComponent, PermissionDaoComponent}
import com.actionml.authserver.model.{AccessToken, Permission}
import com.actionml.oauth2.entities.AccessTokenResponse

import scala.concurrent.Future

trait AuthServiceComponent {

  trait AuthService {
    def authenticateClient(clientId: String, password: String): Future[Boolean]
    def authenticateUser(username: String, password: String): Future[Boolean]
    def createAccessToken(username: String, clientId: String): Future[AccessTokenResponse]
  }

  def authService: AuthService
}

trait AuthServiceComponentImpl extends AuthServiceComponent {
  this: AccessTokenDaoComponent
    with ExecutionContextComponent
    with PermissionDaoComponent =>

  override def authService: AuthService = new AuthServiceImpl

  class AuthServiceImpl extends AuthService {
    override def createAccessToken(bearerToken: String): Future[AccessToken] = {
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
