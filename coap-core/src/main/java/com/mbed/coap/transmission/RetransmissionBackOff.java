/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
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
package com.mbed.coap.transmission;


import static com.mbed.coap.utils.Validations.require;
import com.mbed.coap.CoapConstants;
import java.time.Duration;
import java.util.Random;

@FunctionalInterface
public interface RetransmissionBackOff {

    /**
     * Calculates wait interval for an attempt counter. Note that first
     * attempt starts with value: 1
     *
     * @param attempt attempt counter
     * @return interval in milliseconds
     */
    Duration next(int attempt);

    static RetransmissionBackOff ofFixed(Duration interval) {
        return attempt -> {
            require(attempt > 0);
            return (attempt == 1) ? interval : Duration.ZERO;
        };
    }

    static RetransmissionBackOff ofExponential(Duration first, int maxAttempts) {
        return ofExponential(first, maxAttempts, CoapConstants.ACK_RANDOM_FACTOR);
    }

    static RetransmissionBackOff ofExponential(Duration first, int maxAttempts, float randomFactor) {
        require(randomFactor >= 1);
        require(maxAttempts >= 0);
        final Random rnd = new Random();
        long firstMs = first.toMillis();

        return attempt -> {
            require(attempt > 0);
            if (attempt > maxAttempts + 1) {
                return Duration.ZERO;
            }

            float rndFactor = 1 + (randomFactor - 1) * rnd.nextFloat();
            return Duration.ofMillis((long) (firstMs * rndFactor * (1L << (attempt - 1))));
        };
    }

    static RetransmissionBackOff ofDefault() {
        return ofExponential(CoapConstants.ACK_TIMEOUT, CoapConstants.MAX_RETRANSMIT);
    }
}
