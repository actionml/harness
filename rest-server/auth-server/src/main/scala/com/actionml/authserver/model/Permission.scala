package com.actionml.authserver.model

import com.actionml.authserver.ClientId

case class Permission(clientId: ClientId, roleId: String, resourceId: String)
