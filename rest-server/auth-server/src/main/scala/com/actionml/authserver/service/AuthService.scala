package com.actionml.authserver.service

import java.time.Instant
import java.util.concurrent.ThreadLocalRandom

import com.actionml.authserver.config.AppConfig
import com.actionml.authserver.dal.{AccessTokensDao, UsersDao}
import com.actionml.authserver.exceptions.{AccessDeniedException, TokenExpiredException}
import com.actionml.authserver.model.{AccessToken, Client, UserAccount}
import com.actionml.authserver.util.PasswordUtils
import com.actionml.oauth2.entities.AccessTokenResponse
import com.typesafe.scalalogging.LazyLogging
import scaldi.Injector
import scaldi.akka.AkkaInjectable

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

trait AuthService {
  def authenticateUser(username: String, password: String): Future[Unit]
  def authenticateClient(clientId: String, password: String): Future[Unit]
  def createAccessToken(username: String): Future[AccessTokenResponse]
}

class AuthServiceImpl(implicit injector: Injector) extends AuthService with AuthorizationService with AkkaInjectable
  with PasswordUtils with LazyLogging {

  private implicit val ec = inject[ExecutionContext]
  private val accessTokensDao = inject[AccessTokensDao]
  private val usersDao = inject[UsersDao]
  private val config = inject[AppConfig]

  override def authorize(accessToken: String, roleId: String, resourceId: String): Future[Boolean] = {
    for {
      tokenOpt <- accessTokensDao.findByAccessToken(accessToken)
      token = tokenOpt.getOrElse(throw AccessDeniedException)
      _ <- if (token.createdAt.plusSeconds(config.authServer.accessTokenTtl).isBefore(Instant.now)) {
        accessTokensDao.remove(accessToken).andThen(throw TokenExpiredException)
      } else Future.successful(())
    } yield token.permissions.exists(_.hasAccess(roleId, resourceId))
  }

  override def authenticateUser(userName: String, userPassword: String): Future[Unit] = {
    usersDao.find(userName).map {
      case Some(UserAccount(id, passwordHash, _)) if userName == id && passwordHash == hash(userPassword) => ()
      case _ => throw AccessDeniedException
    }
  }

  override def authenticateClient(clientId: String, clientPassword: String): Future[Unit] = {
    if (config.authServer.clients.contains(Client(clientId, clientPassword))) Future.successful(())
    else Future.failed(AccessDeniedException)
  }

  override def createAccessToken(username: String): Future[AccessTokenResponse] = {
    for {
      userOpt <- usersDao.find(id = username)
      user = userOpt.getOrElse(throw AccessDeniedException)
      token = new Random(ThreadLocalRandom.current()).alphanumeric.take(40).mkString
      _ <- accessTokensDao.store(AccessToken(token, user.id, user.permissions, Instant.now))
    } yield AccessTokenResponse(token, expiresIn = Some(config.authServer.accessTokenTtl))
  }

}
