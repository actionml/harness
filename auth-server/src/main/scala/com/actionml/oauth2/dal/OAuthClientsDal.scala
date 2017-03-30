package com.actionml.oauth2.dal

import com.actionml.oauth2.entities.{Account, OAuthClient}

import scala.concurrent.Future

/**
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  */
trait OAuthClientsDal {
  def validate(clientId: String, clientSecret: String, grantType: String): Future[Boolean]
  def findByClientId(clientId: String): Future[Option[OAuthClient]]
  def findClientCredentials(clientId: String, clientSecret: String): Future[Option[Account]]
}
