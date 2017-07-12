package com.mbed.coap.server.internal;

import com.mbed.coap.server.CoapTcpCSMStorage;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by olesmi01 on 27.07.2017.
 */
public class CoapTcpCSMStorageImpl implements CoapTcpCSMStorage {
    private ConcurrentHashMap<InetSocketAddress, CoapTcpCSM> capabilitiesMap = new ConcurrentHashMap();

    @Override
    public void updateCapability(InetSocketAddress address, CoapTcpCSM newCapabilities) {

        capabilitiesMap.compute(address, (addr, existingCapabilities) -> {
            if (CoapTcpCSM.BASE.equals(newCapabilities)) {
                return null;
            } else {
                return newCapabilities;
            }
        });
    }

    @Override
    public CoapTcpCSM getOrDefault(InetSocketAddress address) {
        return capabilitiesMap.getOrDefault(address, CoapTcpCSM.BASE);
    }

    @Override
    public void remove(InetSocketAddress address) {
        capabilitiesMap.remove(address);
    }
}
