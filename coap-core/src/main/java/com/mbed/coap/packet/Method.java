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
package com.mbed.coap.packet;

import com.mbed.coap.exception.CoapException;


public enum Method {

    GET, POST, PUT, DELETE, FETCH, PATCH, iPATCH;

    public static Method valueOf(int methodCode) throws CoapException {
        switch (methodCode) {
            case 1:
                return GET;
            case 2:
                return POST;
            case 3:
                return PUT;
            case 4:
                return DELETE;
            case 5:
                return FETCH;
            case 6:
                return PATCH;
            case 7:
                return iPATCH;
            default:
                throw new CoapException("Wrong method code");
        }
    }

    public int getCode() {
        return this.ordinal() + 1;
    }
}
