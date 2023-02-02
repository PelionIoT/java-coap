/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
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


public class CoapException extends Exception {

    public static CoapException wrap(Exception ex) {
        if (ex.getCause() instanceof CoapException) {
            return (CoapException) ex.getCause();
        } else {
            return new CoapException(ex.getCause());
        }
    }

    public CoapException(Throwable cause) {
        super(cause);
    }

    public CoapException(String message) {
        super(message);
    }

    public CoapException(String message, Throwable cause) {
        super(message, cause);
    }

    public CoapException(String format, Object... args) {
        super(String.format(format, args));
    }
}
