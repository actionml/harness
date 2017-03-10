package com.actionml.oauth2.dal

import com.actionml.oauth2.entities.Account

import scala.concurrent.Future

/**
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>).
  */
trait AccountsDal{
  def authenticate(name: String, password: String): Future[Option[Account]]
  def findByAccountId(id : String) : Future[Option[Account]]
}
