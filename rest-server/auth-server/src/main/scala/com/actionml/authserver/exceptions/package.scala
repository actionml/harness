package com.actionml.authserver

package object exceptions {
  case object AccessDeniedException extends RuntimeException("Access denied")
  case object TokenExpiredException extends RuntimeException("Token expired")
  case object InvalidRoleSetException extends RuntimeException("Invalid role set")
  trait NotFoundException extends RuntimeException
  case object UserNotFoundException extends RuntimeException("User not found") with NotFoundException
}
