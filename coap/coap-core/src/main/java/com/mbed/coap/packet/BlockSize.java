/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.packet;

/**
 * @author szymon
 */
public enum BlockSize {

    S_16(4), S_32(5), S_64(6), S_128(7), S_256(8), S_512(9), S_1024(10);
    byte szx;

    private BlockSize(int szx) {
        this.szx = (byte) (szx - 4);
    }

    public int getSize() {
        return 1 << (szx + 4);
        //return 2^(szx+4);
    }

}
