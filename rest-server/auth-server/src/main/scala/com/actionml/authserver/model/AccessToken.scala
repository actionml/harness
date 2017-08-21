package com.actionml.authserver.model

import java.time.LocalDateTime

case class AccessToken(token: String, userId: String, permissions: Iterable[Permission], createdAt: LocalDateTime)
