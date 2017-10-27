package com.actionml.authserver.model

import java.time.Instant

case class AccessToken(token: String, userId: String, permissions: Set[Permission], createdAt: Instant)
