package com.actionml.oauth2.dal.memory

import java.security.SecureRandom
import java.sql.Timestamp

import org.joda.time.DateTime
import ru.pavlenov.oauth2.dal.{OAuthAccessTokensDal, OAuthClientsDal}
import ru.pavlenov.oauth2.entities.{Account, OAuthAccessToken, OAuthClient}
import scaldi.Injector
import scaldi.akka.AkkaInjectable

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable
import scala.concurrent.Future
import scala.util.Random

/**
  * ⓭ + 49
  * Какой сам? by Pavlenov Semen 22.01.17.
  */
class OAuthAccessTokensMemoryDal(implicit inj: Injector) extends OAuthAccessTokensDal with AkkaInjectable{

  val oauthClientsDal: OAuthClientsDal = inject[OAuthClientsDal]

  type Key = (String, String)

  private val storage: mutable.HashMap[Key, OAuthAccessToken] = mutable.HashMap.empty

  private def key(account: Account, client: OAuthClient): Key = {
    (account.id, client.id)
  }

  override def create(account: Account, client: OAuthClient): Future[OAuthAccessToken] = Future.successful{
    def randomString(length: Int) = new Random(new SecureRandom()).alphanumeric.take(length).mkString
    val accessToken = randomString(40)
    val refreshToken = randomString(40)
    val createdAt = DateTime.now
    val authAccessToken = OAuthAccessToken(
      accountId = account.id,
      oauthClientId = client.id,
      accessToken = accessToken,
      refreshToken = refreshToken,
      createdAt = createdAt
    )
    storage += (key(account, client) -> authAccessToken)
    authAccessToken
  }

  override def delete(account: Account, client: OAuthClient): Future[Int] = Future.successful {
    storage -= key(account, client)
    1
  }

  override def refresh(account: Account, client: OAuthClient): Future[OAuthAccessToken] = {
    delete(account, client)
    create(account, client)
  }

  override def findByAccessToken(accessToken: String): Future[Option[OAuthAccessToken]] = Future.successful {
    storage.values.find(_.accessToken == accessToken)
  }

  override def findByAuthorized(account: Account, clientId: String): Future[Option[OAuthAccessToken]] = {
    oauthClientsDal.findByClientId(clientId) map {
      case Some(client) => storage.get(key(account, client))
    }
  }

  override def findByRefreshToken(
    refreshToken: String,
    accessTokenExpireSeconds: Int
  ): Future[Option[OAuthAccessToken]] = Future.successful {
    val expireAt = DateTime.now().minusSeconds(accessTokenExpireSeconds).getMillis
    storage.values.find(authAccessToken => authAccessToken.refreshToken == refreshToken && authAccessToken.createdAt.getMillis > expireAt)
  }
}
