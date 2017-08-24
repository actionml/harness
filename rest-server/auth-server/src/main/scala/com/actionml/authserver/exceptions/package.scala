package com.actionml.authserver

package object exceptions {
  case object AccessDeniedException extends RuntimeException("Access denied")
  case object TokenExpiredException extends RuntimeException("Token expired")
  case object InvalidRoleSetException extends RuntimeException("Invalid role set")
}
