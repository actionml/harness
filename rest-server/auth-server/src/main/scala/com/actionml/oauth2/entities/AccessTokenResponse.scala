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

package com.actionml.oauth2.entities

import com.actionml.oauth2.entities.AccessTokenErrorResponse.ErrorCodes.ErrorCode
import com.actionml.oauth2.entities.AccessTokenResponse.TokenTypes
import com.actionml.oauth2.entities.AccessTokenResponse.TokenTypes.TokenType


case class AccessTokenResponse(accessToken: String,
                               tokenType: TokenType = TokenTypes.bearer,
                               expiresIn: Option[Long] = None,
                               refreshToken: Option[String] = None,
                               scope: Option[String] = None)

object AccessTokenResponse {
  object TokenTypes extends Enumeration {
    type TokenType = Value

    val bearer = Value("bearer")
    val mac = Value("MAC")
  }
}

case class AccessTokenErrorResponse(error: ErrorCode,
                                    errorDescription: Option[String],
                                    errorUri: Option[String])

object AccessTokenErrorResponse {
  object ErrorCodes extends Enumeration {
    type ErrorCode = Value

    val invalidRequest = Value("invalid_request")
    val invalidClient = Value("invalid_client")
    val invalidGrant = Value("invalid_grant")
    val unauthorizedClient = Value("unauthorized_client")
    val unsupportedGrantType = Value("unsupported_grant_type")
    val invalidScope = Value("invalid_scope")
  }
}
