package com.actionml.oauth2.dal

import com.actionml.oauth2.entities.OAuthAuthorizationCode

import scala.concurrent.Future

/**
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  */
trait OAuthAuthorizationCodesDal{
  def findByCode(code: String): Future[Option[OAuthAuthorizationCode]]
  def delete(code: String): Future[Int]
}
