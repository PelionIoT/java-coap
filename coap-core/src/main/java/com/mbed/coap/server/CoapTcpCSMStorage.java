package com.mbed.coap.server;

import com.mbed.coap.server.internal.CoapTcpCSM;
import java.net.InetSocketAddress;

/**
 * Created by olesmi01 on 26.07.2017.
 */
public interface CoapTcpCSMStorage {
    void updateCapability(InetSocketAddress address, CoapTcpCSM capabilities);

    CoapTcpCSM getOrDefault(InetSocketAddress address);

    void remove(InetSocketAddress address);
}
