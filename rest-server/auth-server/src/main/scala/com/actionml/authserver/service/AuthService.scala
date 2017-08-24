package com.actionml.authserver.service

import java.time.Instant
import java.util.concurrent.ThreadLocalRandom

import com.actionml.authserver.config.AppConfig
import com.actionml.authserver.dal.{AccessTokensDao, ClientsDao, UsersDao}
import com.actionml.authserver.exceptions.{AccessDeniedException, TokenExpiredException}
import com.actionml.authserver.model.AccessToken
import com.actionml.authserver.util.PasswordUtils
import com.actionml.authserver.{ResourceId, RoleId}
import com.actionml.oauth2.entities.AccessTokenResponse
import scaldi.Injector
import scaldi.akka.AkkaInjectable

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

trait AuthService {
  def authenticateClient(clientId: String, password: String): Future[_]
  def createAccessToken(username: String, password: String, clientId: String): Future[AccessTokenResponse]
  def authorize(accessToken: String, roleId: RoleId, resourceId: ResourceId): Future[Boolean]
}

class AuthServiceImpl(implicit injector: Injector) extends AuthService with AkkaInjectable with PasswordUtils {
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

  override def authenticateClient(clientId: String, password: String): Future[_] = {
    clientsDao.find(clientId).map { client =>
      if (client.id == clientId && client.password == password) ()
      else throw AccessDeniedException
    }
  }

  override def createAccessToken(username: String,
                                 password: String,
                                 clientId: String): Future[AccessTokenResponse] = {
    for {
      userOpt <- usersDao.find(id = username, hash(password), clientId = clientId)
      user = userOpt.getOrElse(throw AccessDeniedException)
      token = new Random(ThreadLocalRandom.current()).alphanumeric.take(40).mkString
      _ <- accessTokensDao.store(AccessToken(token, user.id, user.permissions, Instant.now))
    } yield AccessTokenResponse(token, expiresIn = Some(config.authServer.accessTokenTtl))
  }

}
