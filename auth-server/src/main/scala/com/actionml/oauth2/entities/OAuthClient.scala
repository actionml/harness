package com.actionml.oauth2.entities

import org.joda.time.DateTime

case class OAuthClient(
  id: String,
  ownerId: String,
  grantType: String,
  clientSecret: String,
  redirectUri: Option[String],
  createdAt: DateTime
)
