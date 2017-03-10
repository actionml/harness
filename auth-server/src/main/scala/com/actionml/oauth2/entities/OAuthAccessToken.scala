package com.actionml.oauth2.entities

import org.joda.time.DateTime

case class OAuthAccessToken(
  accountId: String,
  oauthClientId: String,
  accessToken: String,
  refreshToken: String,
  createdAt: DateTime
)
