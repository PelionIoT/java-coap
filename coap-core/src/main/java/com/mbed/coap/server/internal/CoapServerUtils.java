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
package com.mbed.coap.server.internal;

import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.utils.Callback;
import com.mbed.coap.utils.RequestCallback;

/**
 * Created by olesmi01 on 14.08.2017.
 * Minor utilities for Protocol CoAP servers
 */
public class CoapServerUtils {
    public static void assume(boolean assumeCondition, String errorMessage) {
        if (!assumeCondition) {
            throw new IllegalStateException(errorMessage);
        }
    }

    static RequestCallback wrapCallback(Callback<CoapPacket> callback) {
        if (callback == null) {
            throw new NullPointerException();
        }
        if (callback instanceof RequestCallback) {
            return ((RequestCallback) callback);
        }

        return new InternalRequestCallback(callback);
    }

    static class InternalRequestCallback implements RequestCallback {
        private final Callback<CoapPacket> callback;

        InternalRequestCallback(Callback<CoapPacket> callback) {
            this.callback = callback;
        }

        @Override
        public void call(CoapPacket packet) {
            callback.call(packet);
        }

        @Override
        public void callException(Exception ex) {
            callback.callException(ex);
        }

        @Override
        public void onSent() {
            //ignore
        }
    }
}
