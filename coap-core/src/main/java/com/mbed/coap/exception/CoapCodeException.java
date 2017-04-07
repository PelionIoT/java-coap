/**
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mbed.coap.exception;

import com.mbed.coap.packet.Code;

/**
 * @author szymon
 */
public class CoapCodeException extends CoapException {

    private final Code code;

    public CoapCodeException(Code code) {
        super(code.toString().substring(1).replace("_", " "));
        this.code = code;
    }

    public CoapCodeException(Code code, Throwable throwable) {
        super(code.toString().substring(1).replace("_", " "), throwable);
        this.code = code;
    }

    public CoapCodeException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public CoapCodeException(Code code, String message, Throwable throwable) {
        super(code.toString().substring(1).replace("_", " ") + " " + message, throwable);
        this.code = code;
    }

    /**
     * @return the code
     */
    public Code getCode() {
        return code;
    }

}
