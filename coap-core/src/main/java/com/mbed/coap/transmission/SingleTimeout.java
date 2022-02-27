/**
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
package com.mbed.coap.transmission;


public class SingleTimeout implements TransmissionTimeout {

    long timeout;
    long multicastTimeout = 2000;

    public SingleTimeout(long timeoutMili) {
        this.timeout = timeoutMili;
    }

    public SingleTimeout(long timeoutMili, long multicastTimeout) {
        this.timeout = timeoutMili;
        this.multicastTimeout = multicastTimeout;
    }

    @Override
    public long getTimeout(int attempt) {
        if (attempt > 1) {
            return -1;
        }
        if (attempt <= 0) {
            throw new IllegalArgumentException("attempt can not be less than 0");
        }
        return timeout;
    }

    @Override
    public long getMulticastTimeout(int attempt) {
        if (attempt == 1) {
            return multicastTimeout;
        }
        return -1;
    }

}
