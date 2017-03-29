package com.actionml.core.validate

/**
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  *         Created by Pavlenov Semen on 18.03.17.
  */
sealed trait ValidateError

final case class ParseError(message: String) extends ValidateError
final case class MissingParams(message: String) extends ValidateError
final case class WrongParams(message: String) extends ValidateError
final case class EventOutOfSequence(message: String) extends ValidateError
