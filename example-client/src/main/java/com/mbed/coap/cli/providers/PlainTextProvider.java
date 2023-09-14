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
package com.mbed.coap.cli.providers;

import com.mbed.coap.cli.TransportProvider;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.javassl.CoapSerializer;
import com.mbed.coap.transport.javassl.SocketClientTransport;
import com.mbed.coap.transport.udp.DatagramSocketTransport;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import javax.net.SocketFactory;

public class PlainTextProvider extends TransportProvider {

    @Override
    public CoapTransport createUDP(CoapSerializer coapSerializer, InetSocketAddress destAdr, KeyStore ks) {
        return new DatagramSocketTransport(0);
    }

    @Override
    public CoapTransport createTCP(CoapSerializer coapSerializer, InetSocketAddress destAdr, KeyStore ks) {
        return new SocketClientTransport(destAdr, SocketFactory.getDefault(), coapSerializer, true);
    }
}
