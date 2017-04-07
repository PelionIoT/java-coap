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
package com.mbed.coap.utils;

/**
 * Simple implementation for {@link Callback} interface that stores the latest value only.
 * @author nordav01
 */
public class SimpleCallbackAdapter<V> implements Callback<V> {

    private V value;
    private Exception exception;

    @Override
    public void call(V value) {
        this.value = value;
    }

    @Override
    public void callException(Exception exception) {
        this.exception = exception;
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    public V get() throws Exception {
        if (exception != null) {
            throw exception;
        }
        return value;
    }

}
