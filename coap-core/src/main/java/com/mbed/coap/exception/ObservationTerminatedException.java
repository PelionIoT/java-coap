/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
 * Copyright (c) 2023 Izuma Networks. All rights reserved.
 * 
 * SPDX-License-Identifier: Apache-2.0
 * 
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

import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.transport.TransportContext;

/**
 * @author szymon
 */
public class ObservationTerminatedException extends CoapException {

    private final CoapPacket packet;
    private final TransportContext context;

    public ObservationTerminatedException(CoapPacket packet, TransportContext context) {
        super("Coap message exception");
        this.packet = packet;
        this.context = context;
    }

    public CoapPacket getNotification() {
        return packet;
    }

    public TransportContext getTransportContext() {
        return context;
    }
}
