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

import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Container class for raw IP and port without hostname. Avoids deterministic
 * problem of serializing InetSocketAddress with hostname.
 *
 * @author szymon
 */
public class IpPortAddress implements Serializable {
    private static final long serialVersionUID = 1L;

    private final byte[] ip;
    private final int port;
    private transient String textRepresentation;

    public IpPortAddress(byte[] ip, int port) {
        if (ip.length != 4 && ip.length != 16) {
            throw new IllegalArgumentException("IP address length out of range");
        }
        if (port < 0 || port > 0xFFFF) {
            throw new IllegalArgumentException("port out of range");
        }
        this.ip = ip;
        this.port = port;
    }

    public IpPortAddress(InetSocketAddress address) {
        this(address.getAddress().getAddress(), address.getPort());
    }

    public static IpPortAddress of(InetSocketAddress address) {
        return new IpPortAddress(address);
    }

    public static IpPortAddress local(int localPort) {
        return new IpPortAddress(new byte[]{127, 0, 0, 1}, localPort);
    }

    public byte[] getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        if (textRepresentation == null) {
            if (ip.length == 4) {
                //ipv4
                textRepresentation = (ip[0] & 0xff) + "." + (ip[1] & 0xff) + "." + (ip[2] & 0xff) + "." + (ip[3] & 0xff) + ":" + port;
            } else {
                textRepresentation = ipv6ToString();
            }
        }
        return textRepresentation;
    }

    private String ipv6ToString() {
        StringBuilder sb = new StringBuilder(39);
        sb.append('[');
        boolean doneTrimming = false;
        boolean isTrimming = false;
        for (int i = 0; i < ip.length / 2; i++) {
            int ip6part = ((ip[i << 1] << 8) & 0xff00) | (ip[(i << 1) + 1] & 0xff);
            if (ip6part == 0 && !doneTrimming) {
                if (!isTrimming) {
                    sb.append(':');
                }
                isTrimming = true;
            } else {
                sb.append(Integer.toHexString(ip6part));
                doneTrimming |= isTrimming;
                if (i < (ip.length / 2) - 1) {
                    sb.append(':');
                }
            }
        }
        sb.append("]:").append(port);
        return sb.toString();
    }

    public InetSocketAddress toInetSocketAddress() {
        try {
            return new InetSocketAddress(InetAddress.getByAddress(ip), port);
        } catch (UnknownHostException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 29 * hash + Arrays.hashCode(this.ip);
        hash = 29 * hash + this.port;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final IpPortAddress other = (IpPortAddress) obj;
        if (!Arrays.equals(this.ip, other.ip)) {
            return false;
        }
        if (this.port != other.port) {
            return false;
        }
        return true;
    }

}
