package com.actionml

package object authserver {
  type BearerToken = String
  type AccessToken = String
  type RoleId = String
  type ResourceId = String
  type Permissions = Map[RoleId, ResourceId]

  object ResourceId {
    val * = "*"
  }
}
