package com.actionml.oauth2.entities

import org.joda.time.DateTime

case class OAuthAuthorizationCode(
  accountId: String,
  oauthClientId: String,
  code: String,
  redirectUri: Option[String],
  createdAt: DateTime
)
