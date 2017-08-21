package com.actionml.authserver.service

import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.concurrent.ThreadLocalRandom

import com.actionml.authserver.config.AppConfig
import com.actionml.authserver.dal.{AccessTokensDao, ClientsDao, UsersDao}
import com.actionml.authserver.exceptions.{AccessDeniedException, TokenExpiredException}
import com.actionml.authserver.model.{AccessToken, Client, Permission}
import com.actionml.authserver.{ResourceId, RoleId}
import com.actionml.oauth2.entities.AccessTokenResponse
import scaldi.Injector
import scaldi.akka.AkkaInjectable

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

trait AuthService {
  def authenticateClient(clientId: String, password: String): Future[Boolean]
  def createAccessToken(username: String, password: String, permissions: Iterable[Permission]): Future[AccessTokenResponse]
  def authorize(accessToken: String, roleId: RoleId, resourceId: ResourceId): Future[Boolean]
}

class AuthServiceImpl(implicit injector: Injector) extends AuthService with AkkaInjectable {
  private implicit val ec = inject[ExecutionContext]
  private val accessTokensDao = inject[AccessTokensDao]
  private val usersDao = inject[UsersDao]
  private val clientsDao = inject[ClientsDao]
  private val config = inject[AppConfig]


  def authorize(accessToken: String, roleId: String, resourceId: String): Future[Boolean] = {
    accessTokensDao.findByAccessToken(accessToken)
      .collect {
        case Some(AccessToken(_, _, permissions, createdAt)) =>
          if (createdAt.isBefore(LocalDateTime.now)) throw TokenExpiredException
          else permissions.exists(_.hasAccess(roleId, resourceId))
        case _ => false
      }
  }

  override def authenticateClient(clientId: String, password: String): Future[Boolean] = {
    clientsDao.find(clientId).collect {
      case Client(id, secretHash) if id == clientId => password == secretHash
      case _ => false
    }
  }

  override def createAccessToken(username: String, password: String, permissions: Iterable[Permission]): Future[AccessTokenResponse] = {
    val token = new Random(ThreadLocalRandom.current()).alphanumeric.take(40).mkString
    for {
      userOpt <- usersDao.find(username, hash(password))
      user = userOpt.getOrElse(throw AccessDeniedException)
      _ <- accessTokensDao.store(AccessToken(token, user.id, permissions, LocalDateTime.now))
    } yield AccessTokenResponse(token, expiresIn = Some(config.authServer.accessTokenTtl))
  }


  private val sha = MessageDigest.getInstance("SHA")
  private def hash(x: String): String = {
    new String(sha.digest(x.getBytes()))
  }
}
