/*
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
package com.mbed.coap.transmission;

import com.mbed.coap.CoapConstants;
import java.util.Random;

/**
 * @author szymon
 */
public class CoapTimeout implements TransmissionTimeout {

    public static final int MULTICAST_TIMEOUT = 2000;
    private final Random rnd = new Random();
    private final long timeoutBase;
    private final int maxRetransmit;

    public CoapTimeout() {
        this(CoapConstants.ACK_TIMEOUT, CoapConstants.MAX_RETRANSMIT);
    }

    public CoapTimeout(long timeoutBase) {
        this(timeoutBase, CoapConstants.MAX_RETRANSMIT);
    }

    public CoapTimeout(long timeoutBase, int maxRetransmit) {
        this.timeoutBase = timeoutBase;
        this.maxRetransmit = maxRetransmit;
    }

    @Override
    public long getTimeout(int attemptCounter) {
        if (attemptCounter > maxRetransmit + 1) {
            return -1;
        }
        if (attemptCounter <= 0) {
            throw new IllegalArgumentException("attempt can not be less than 0");
        }
        float rndFactor = 1 + (CoapConstants.ACK_RANDOM_FACTOR - 1) * rnd.nextFloat();
        return (long) (timeoutBase * rndFactor * (1 << (attemptCounter - 1)));
    }

    @Override
    public long getMulticastTimeout(int attempt) {
        if (attempt == 1) {
            return MULTICAST_TIMEOUT;
        }
        return -1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CoapTimeout that = (CoapTimeout) o;

        if (maxRetransmit != that.maxRetransmit) {
            return false;
        }
        if (timeoutBase != that.timeoutBase) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = (int) (timeoutBase ^ (timeoutBase >>> 32));
        result = 31 * result + maxRetransmit;
        return result;
    }
}
