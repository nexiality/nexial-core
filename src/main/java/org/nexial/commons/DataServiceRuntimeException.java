/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.commons;

/**
 * @author $Author: lium $
 */
public class DataServiceRuntimeException extends RuntimeException {
    private static final long serialVersionUID = 1018609467367298390L;
    private Object offense;
    private String detailMessage;

    public DataServiceRuntimeException(Object offense) {
        detailMessage = "target: " + offense.getClass();
        this.offense = offense;
    }

    public DataServiceRuntimeException(String message, Object offense) {
        super(message);
        detailMessage = message + ", target: " + offense.getClass();
        this.offense = offense;
    }

    public DataServiceRuntimeException(String message, Throwable cause, Object offense) {
        super(message, cause);
        detailMessage = message + "; target: " + offense.getClass();
        this.offense = offense;
    }

    public DataServiceRuntimeException(Throwable cause, Object offense) {
        super(cause);
        detailMessage = "target: " + offense.getClass();
        this.offense = offense;
    }

    public Object getOffense() { return offense; }

    public String getDetailMessage() { return detailMessage; }
}
