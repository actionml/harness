package com.actionml.oauth2.dal.memory

import com.actionml.oauth2.dal.OAuthAuthorizationCodesDal
import com.actionml.oauth2.entities.OAuthAuthorizationCode

import scala.collection.mutable
import scala.concurrent.Future

/**
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
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
