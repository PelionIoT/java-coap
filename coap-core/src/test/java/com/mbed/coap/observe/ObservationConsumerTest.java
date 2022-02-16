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
package com.mbed.coap.observe;

import static com.mbed.coap.observe.ObservationConsumer.*;
import static com.mbed.coap.packet.CoapResponse.*;
import static org.mockito.BDDMockito.*;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.utils.FutureQueue;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ObservationConsumerTest {

    private FutureQueue<CoapResponse> queue = new FutureQueue<>();
    private Function<CoapResponse, Boolean> consumer = Mockito.mock(Function.class);

    @BeforeEach
    void setUp() {
        reset(consumer);

        given(consumer.apply(any())).willReturn(true);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(consumer);
    }

    @Test
    void shouldConsume() {
        consumeFrom(queue, consumer);

        // when
        queue.put(ok("1"));
        queue.put(ok("2"));

        // then
        verify(consumer).apply(ok("1"));
        verify(consumer).apply(ok("2"));
    }

    @Test
    void shouldStopConsuming() {
        given(consumer.apply(any())).willReturn(true, false);
        consumeFrom(queue, consumer);

        // when
        queue.put(ok("1"));
        queue.put(ok("2"));
        queue.put(ok("3"));
        queue.put(ok("4"));

        // then
        verify(consumer).apply(ok("1"));
        verify(consumer).apply(ok("2"));
    }

    @Test
    void doNothingWhenNull() {
        consumeFrom(null, consumer);

        verifyNoInteractions(consumer);
    }
}