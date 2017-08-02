package com.actionml.security

package object model {
  type Credentials = String
  type Role = String
  type ResourceId = String
  type Permissions = Map[Role, ResourceId]

  object ResourceId {
    val * = "*"
  }
}
