/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.server;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public final class MessageIdSupplierImpl implements MessageIdSupplier {

    private final AtomicInteger globalMid;

    public MessageIdSupplierImpl() {
        this(new Random().nextInt(0xFFFF));
    }

    public MessageIdSupplierImpl(int initMid) {
        this.globalMid = new AtomicInteger(initMid);
    }

    @Override
    public int getNextMID() {
        return 0xFFFF & (globalMid.incrementAndGet());
    }
}
