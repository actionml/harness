package com.actionml.oauth2.dal.memory

import java.security.MessageDigest

import com.actionml.oauth2.dal.AccountsDal
import com.actionml.oauth2.entities.Account

import scala.collection.mutable
import scala.concurrent.Future

/**
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  */
class AccountsMemoryDal extends AccountsDal{

  private val storage: mutable.Map[String, Account] = mutable.HashMap(
    "account_id" -> Account(
      id = "account_id",
      name = "account_name",
      password = "account_password"
    )
  )

  private def digestString(s: String): String = {
    val md = MessageDigest.getInstance("SHA-1")
    md.update(s.getBytes)
    md.digest.foldLeft("") { (s, b) ⇒
      s + "%02x".format(if (b < 0) b + 256 else b)
    }
  }

  override def authenticate(name: String, password: String): Future[Option[Account]] = Future.successful {
    val hashedPassword = digestString(password)
    storage.values.find(account ⇒ account.name == name && account.password == hashedPassword)
  }

  override def findByAccountId(id: String): Future[Option[Account]] = Future.successful {
    storage.get(id)
  }
}
