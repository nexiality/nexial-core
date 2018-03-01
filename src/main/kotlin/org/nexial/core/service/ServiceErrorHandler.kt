/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.service

import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.springframework.core.Ordered.HIGHEST_PRECEDENCE
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.lang.Exception

@Order(HIGHEST_PRECEDENCE)
@ControllerAdvice
class ServiceErrorHandler : ResponseEntityExceptionHandler() {
    override fun handleExceptionInternal(ex: Exception?,
                                         body: Any?,
                                         headers: HttpHeaders?,
                                         status: HttpStatus,
                                         request: WebRequest?): ResponseEntity<Any> {
        return ResponseEntity(
            ServiceRequestError(status = status,
                                                        message = body.toString(),
                                                        debugMessage = ArrayUtils.toString(
                                                            ExceptionUtils.getRootCause(ex))),
            status)
    }
}