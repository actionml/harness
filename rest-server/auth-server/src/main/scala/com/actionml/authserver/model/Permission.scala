package com.actionml.authserver.model

case class Permission(clientId: ClientId, roleId: String, resourceId: String)
