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
package com.mbed.coap.server;

import com.mbed.coap.server.internal.CoapTcpCSM;
import java.net.InetSocketAddress;

/**
 * Created by olesmi01 on 26.07.2017.
 * CoAP over TCP capabilities and settings storage interface
 */
public interface CoapTcpCSMStorage {
    void put(InetSocketAddress address, CoapTcpCSM capabilities);

    CoapTcpCSM getOrDefault(InetSocketAddress address);

    void remove(InetSocketAddress address);
}
