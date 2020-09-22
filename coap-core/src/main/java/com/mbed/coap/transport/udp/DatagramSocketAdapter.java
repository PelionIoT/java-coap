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
package com.mbed.coap.transport.udp;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

public class DatagramSocketAdapter extends DatagramSocket implements BlockingSocket {
    public DatagramSocketAdapter(InetSocketAddress bindSocket) throws SocketException {
        super(bindSocket);
        initialize();
    }

    public DatagramSocketAdapter(int port) throws SocketException {
        super(port);
        initialize();
    }

    @Override
    public InetSocketAddress getBoundAddress() {
        return (InetSocketAddress) getLocalSocketAddress();
    }

    private final void initialize() throws SocketException {
        setSoTimeout(200);
    }

}
