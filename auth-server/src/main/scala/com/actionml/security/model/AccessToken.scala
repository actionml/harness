package com.actionml.security.model

import java.time.LocalDateTime

case class AccessToken(accessToken: String, permissions: Iterable[Permission], createdAt: LocalDateTime)
