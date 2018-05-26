/*
 * Copyright ActionML, LLC under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * ActionML licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
final case class ResourceNotFound(message: String) extends ValidateError
final case class NotImplemented(message: String = "Not implemented") extends ValidateError
final case class ValidRequestExecutionError(message: String = "Errors executing a valid request") extends ValidateError
