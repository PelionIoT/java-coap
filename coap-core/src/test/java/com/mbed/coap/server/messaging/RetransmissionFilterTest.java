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
package com.mbed.coap.server.messaging;

import static java.time.Duration.ZERO;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.reset;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.verifyNoMoreInteractions;
import com.mbed.coap.exception.CoapTimeoutException;
import com.mbed.coap.transmission.RetransmissionBackOff;
import com.mbed.coap.utils.MockTimer;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RetransmissionFilterTest {

    private final MockTimer timer = new MockTimer();
    private final RetransmissionBackOff backoff = new DoubleRetransmissionBackOff();

    private final RetransmissionFilter<String, String> filter = new RetransmissionFilter<>(timer, backoff, r -> !r.startsWith("NON"));
    private final Service<String, String> service = Mockito.mock(Service.class);
    private final Service<String, String> filteredSrv = filter.then(service);
    private CompletableFuture<String> promise;
    private CompletableFuture<String> resp;

    @BeforeEach
    void setUp() {
        reset(service);

        given(service.apply(any())).will(__ -> {
            promise = new CompletableFuture<>();
            return promise;
        });
    }

    @AfterEach
    void tearDown() {
        assertTrue(timer.isEmpty());
        verifyNoMoreInteractions(service);
    }

    @Test
    void handleResponse_noRetransmission() {
        resp = filteredSrv.apply("REQ1");
        assertFalse(resp.isDone());

        // when
        promise.complete("resp1");

        // then
        assertEquals("resp1", resp.join());
        verify(service).apply(any());
    }

    @Test
    void shouldForward_nonRetransmittedMessage() {
        resp = filteredSrv.apply("NON1");
        assertFalse(resp.isDone());
        assertEquals(0, timer.size());

        // when
        promise.complete("resp1");

        // then
        assertEquals("resp1", resp.join());
        verify(service).apply(any());
    }

    @Test
    void shouldTimeout_when_noResponse() {
        resp = filteredSrv.apply("REQ1");
        CompletableFuture<String> firstPromise = promise;

        // when
        timer.runAll();
        timer.runAll();
        timer.runAll();

        // then
        assertThatThrownBy(resp::join).hasCauseExactlyInstanceOf(CoapTimeoutException.class);
        verify(service, times(3)).apply(any());
        assertTrue(firstPromise.isCompletedExceptionally());
    }

    @Test
    void shouldSucceed_after_retransmission() {
        resp = filteredSrv.apply("REQ1");

        // when
        timer.runAll();
        promise.complete("resp1");

        // then
        assertEquals("resp1", resp.join());
        verify(service, times(2)).apply(any());
    }

    @Test
    void shouldFail_after_retransmission() {
        resp = filteredSrv.apply("REQ1");

        // when
        timer.runAll();
        promise.completeExceptionally(new IOException());

        // then
        assertThatThrownBy(resp::join).hasCauseExactlyInstanceOf(IOException.class);
        verify(service, times(2)).apply(any());
    }

    // - non-retryable message

    static class DoubleRetransmissionBackOff implements RetransmissionBackOff {
        @Override
        public Duration next(int attemptCounter) {
            switch (attemptCounter) {
                case 1:
                    return ofSeconds(1);
                case 2:
                    return ofSeconds(2);
                case 3:
                    return ofSeconds(3);
                default:
                    return ZERO;
            }
        }
    }
}
