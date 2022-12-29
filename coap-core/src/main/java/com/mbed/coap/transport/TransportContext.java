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
package com.mbed.coap.transport;

import com.mbed.coap.packet.MessageType;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * This class provides transport context information.
 */
@FunctionalInterface
public interface TransportContext {

    TransportContext EMPTY = key -> null;
    TransportContext NON_CONFIRMABLE = EMPTY.add(MessageType.NonConfirmable, true);

    Object get(Object key);

    default TransportContext add(Object key, Object val) {
        if (val == null) {
            return this;
        } else {
            return add(key, () -> val);
        }
    }

    default TransportContext add(Object key, Supplier func) {
        Objects.requireNonNull(key);

        return k -> {
            if (key.equals(k)) {
                return func.get();
            } else {
                return this.get(k);
            }
        };
    }

    default <T> T getAndCast(Object key, Class<T> clazz) {
        Object val = get(key);

        if (clazz.isInstance(val)) {
            return clazz.cast(val);
        } else {
            return null;
        }
    }

}
