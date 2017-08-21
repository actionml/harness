package com.actionml.authserver.model

import com.actionml.authserver.ResourceId

case class Permission(clientId: String, resources: Map[String, Iterable[ResourceId]]) {
  def hasAccess(roleId: String, resourceId: ResourceId): Boolean = {
    resources.get(roleId).exists(_.exists(x => x == ResourceId.* || x == resourceId))
  }
}
