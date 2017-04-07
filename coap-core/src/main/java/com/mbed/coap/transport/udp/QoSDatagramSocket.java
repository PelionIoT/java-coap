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
package com.mbed.coap.transport.udp;

import java.lang.reflect.Field;
import java.net.DatagramSocket;
import java.net.DatagramSocketImpl;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOptions;

/**
 * DatagramSocket implementation that changes traffic class in non-synchronous
 * way to avoid blocking with receive method.
 *
 * @author szymon
 */
public class QoSDatagramSocket extends DatagramSocket {

    private final DatagramSocketImpl impl;

    public QoSDatagramSocket(SocketAddress bindSocketAddress) throws SocketException {
        super(bindSocketAddress);
        try {
            Field implField = DatagramSocket.class.getDeclaredField("impl");
            implField.setAccessible(true);

            this.impl = (DatagramSocketImpl) implField.get(this);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Non-synchronized on purpose (TODO refactor away the need to do this)
     */
    @SuppressWarnings("sync-override")
    @Override
    public void setTrafficClass(int tc) throws SocketException {
        if (tc < 0 || tc > 255) {
            throw new IllegalArgumentException("tc is not in range 0 -- 255");
        }
        if (isClosed()) {
            throw new SocketException("Socket is closed");
        }
        impl.setOption(SocketOptions.IP_TOS, tc);

    }

}
