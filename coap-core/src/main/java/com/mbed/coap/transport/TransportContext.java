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
package com.mbed.coap.transport;

import java.util.function.Supplier;

/**
 * This class provides transport context information.
 *
 * Created by szymon
 */
public abstract class TransportContext {

    public static final TransportContext NULL = new TransportContext() {
        @Override
        public Object get(Object key) {
            return null;
        }
    };

    public abstract Object get(Object key);

    public TransportContext add(Object key, Object val) {
        if (val == null) {
            return this;
        } else {
            return add(key, () -> val);
        }
    }

    public TransportContext add(Object key, Supplier func) {
        if (key == null) {
            throw new NullPointerException();
        }

        return new TransportContext() {
            @Override
            public Object get(Object k) {
                if (key.equals(k)) {
                    return func.get();
                } else {
                    return this.get(k);
                }
            }
        };
    }

    public <T> T getAndCast(Object key, Class<T> clazz) {
        Object val = get(key);
        if (val != null && clazz.isInstance(val)) {
            return ((T) val);
        } else {
            return null;
        }
    }

}
