package com.actionml.authserver.service

import java.time.LocalDateTime

import com.actionml.authserver.{ResourceId, RoleId}
import com.actionml.authserver.dal.{AccessTokenDao, PermissionDao}
import com.actionml.authserver.model.{AccessToken, Client}
import com.actionml.oauth2.entities.AccessTokenResponse
import scaldi.Injector
import scaldi.akka.AkkaInjectable

import scala.concurrent.{ExecutionContext, Future}

trait AuthService {
  def authenticateClient(clientId: String, password: String): Future[Client]
  def authenticateUser(username: String, password: String): Future[Boolean]
  def createAccessToken(username: String, clientId: String): Future[AccessTokenResponse]
  def authorize(accessToken: String, roleId: RoleId, resourceId: ResourceId): Future[Boolean]
}

class AuthServiceImpl(implicit injector: Injector) extends AuthService with AkkaInjectable {
  private implicit val ec = inject[ExecutionContext]
  private val permissionDao = inject[PermissionDao]
  private val accessTokenDao = inject[AccessTokenDao]


  def authorize(accessToken: String, roleId: String, resourceId: String): Future[Boolean] = {
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

  override def authenticateClient(clientId: String, password: String): Future[Client] = ???

  override def authenticateUser(username: String, password: String): Future[Boolean] = ???

  override def createAccessToken(username: String, clientId: String): Future[AccessTokenResponse] = ???
}
