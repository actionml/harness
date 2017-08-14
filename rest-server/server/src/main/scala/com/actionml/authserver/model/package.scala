package com.actionml.authserver

package object model {
  type BearerToken = String
  type AccessToken = String
  type Role = String
  type ResourceId = String
  type Permissions = Map[Role, ResourceId]

  object ResourceId {
    val * = "*"
  }
}
