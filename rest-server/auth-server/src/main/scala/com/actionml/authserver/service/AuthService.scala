package com.actionml.authserver.service

import java.security.MessageDigest
import java.time.LocalDateTime

import com.actionml.authserver.dal.{AccessTokensDao, ClientsDao, PermissionsDao}
import com.actionml.authserver.model.{AccessToken, Client}
import com.actionml.authserver.{ResourceId, RoleId}
import com.actionml.oauth2.entities.AccessTokenResponse
import scaldi.Injector
import scaldi.akka.AkkaInjectable

import scala.concurrent.{ExecutionContext, Future}

trait AuthService {
  def authenticateClient(clientId: String, password: String): Future[Boolean]
  def authenticateUser(username: String, password: String): Future[Boolean]
  def createAccessToken(username: String, clientId: String): Future[AccessTokenResponse]
  def authorize(accessToken: String, roleId: RoleId, resourceId: ResourceId): Future[Boolean]
}

class AuthServiceImpl(implicit injector: Injector) extends AuthService with AkkaInjectable {
  private implicit val ec = inject[ExecutionContext]
  private val accessTokensDao = inject[AccessTokensDao]
  private val permissionsDao = inject[PermissionsDao]
  private val clientsDao = inject[ClientsDao]


  def authorize(accessToken: String, roleId: String, resourceId: String): Future[Boolean] = {
    accessTokensDao.findByAccessToken(accessToken)
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

  override def authenticateClient(clientId: String, password: String): Future[Boolean] = {
    clientsDao.find(clientId).collect {
      case Client(id, secretHash) if id == clientId => hash(password) == secretHash
      case _ => false
    }
  }

  private def hash(x: String): String = {
    val md = MessageDigest.getInstance("SHA")
    new String(md.digest(x.getBytes()))
  }

  override def authenticateUser(username: String, password: String): Future[Boolean] = Future.successful(true)

  override def createAccessToken(username: String, clientId: String): Future[AccessTokenResponse] = ???
}
