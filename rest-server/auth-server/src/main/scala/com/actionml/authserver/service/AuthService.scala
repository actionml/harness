package com.actionml.authserver.service

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
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
    for {
      tokenOpt <- accessTokensDao.findByAccessToken(accessToken)
      token = tokenOpt.getOrElse(throw AccessDeniedException)
      _ <- if (token.createdAt.plusSeconds(config.authServer.accessTokenTtl).isBefore(Instant.now)) {
        accessTokensDao.remove(accessToken).andThen(throw TokenExpiredException)
      } else Future.successful(())
    } yield token.permissions.exists(_.hasAccess(roleId, resourceId))
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
      _ <- accessTokensDao.store(AccessToken(token, user.id, permissions, Instant.now))
    } yield AccessTokenResponse(token, expiresIn = Some(config.authServer.accessTokenTtl))
  }


  private val sha1 = MessageDigest.getInstance("SHA-1")
  private def hash(x: String): String = {
    val digest = sha1.digest(x.getBytes(StandardCharsets.UTF_8))
    String.format("%064x", new BigInteger(1, digest))
  }
}
