/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
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

import static com.mbed.coap.utils.Exceptions.*;
import static com.mbed.coap.utils.Validations.*;
import com.mbed.coap.packet.CoapResponse;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class ObservationConsumer implements Function<CoapResponse, Boolean> {

    private final BlockingQueue<CoapResponse> queue = new ArrayBlockingQueue<>(100);

    @Override
    public Boolean apply(CoapResponse obs) {
        assume(queue.offer(obs));
        return true;
    }

    public CoapResponse next() {
        return reThrow(() -> queue.poll(10, TimeUnit.SECONDS));
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
