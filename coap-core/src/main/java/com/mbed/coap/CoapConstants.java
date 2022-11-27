/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
 * Copyright (C) 2011-2021 ARM Limited. All rights reserved.
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
package com.mbed.coap;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * CoAP constants that are defined in RFC 7252 document
 */
public final class CoapConstants {

    public static final int DEFAULT_PORT = 5683;
    public static final String WELL_KNOWN_CORE = "/.well-known/core";
    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
    public static final Duration ACK_TIMEOUT = Duration.ofSeconds(2);
    public static final float ACK_RANDOM_FACTOR = 1.5f;
    public static final Short MAX_RETRANSMIT = 4;

    private CoapConstants() {
    }

}
