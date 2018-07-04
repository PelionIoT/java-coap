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
package com.mbed.coap.utils;

import com.mbed.coap.packet.CoapPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by szymon
 */
public interface RequestCallback extends Callback<CoapPacket> {
    Logger LOGGER = LoggerFactory.getLogger(RequestCallback.class);

    RequestCallback NULL = new RequestCallback() {

        @Override
        public void onSent() {
            //ignore
        }

        @Override
        public void call(CoapPacket packet) {
            //ignore
        }

        @Override
        public void callException(Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
        }
    };

    void onSent();

}
