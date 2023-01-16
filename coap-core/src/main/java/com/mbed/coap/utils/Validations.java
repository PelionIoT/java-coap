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
package com.mbed.coap.utils;

/**
 * Minor utilities for Protocol CoAP servers
 */
public class Validations {
    public static void assume(boolean assumeCondition, String errorMessage) {
        if (!assumeCondition) {
            throw new IllegalStateException(errorMessage);
        }
    }

    public static void assume(boolean assumeCondition) {
        if (!assumeCondition) {
            throw new IllegalStateException();
        }
    }

    public static void require(boolean requireCondition, String errorMessage) {
        if (!requireCondition) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    public static void require(boolean requireCondition) {
        require(requireCondition, null);
    }

}
