package com.actionml.oauth2.dal.memory

import ru.pavlenov.oauth2.dal.OAuthAuthorizationCodesDal
import ru.pavlenov.oauth2.entities.{Account, OAuthAccessToken, OAuthAuthorizationCode, OAuthClient}

import scala.collection.mutable
import scala.concurrent.Future

/**
  * ⓭ + 29
  * Какой сам? by Pavlenov Semen 22.01.17.
  */
class OAuthAuthorizationCodesMemoryDal extends OAuthAuthorizationCodesDal{

  private val storage: mutable.HashMap[String, OAuthAuthorizationCode] = mutable.HashMap.empty

  override def findByCode(code: String): Future[Option[OAuthAuthorizationCode]] = Future.successful {
    storage.get(code)
  }

  override def delete(code: String): Future[Int] = Future.successful {
    storage -= code
    1
  }
}
