package com.actionml.authserver.model

import java.time.LocalDateTime

case class AccessToken(accessToken: String, permissions: Iterable[Permission], createdAt: LocalDateTime)
