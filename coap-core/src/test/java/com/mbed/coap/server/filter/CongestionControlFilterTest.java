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
package com.mbed.coap.server.filter;

import static java.util.concurrent.CompletableFuture.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import com.mbed.coap.exception.TooManyRequestsForEndpointException;
import com.mbed.coap.utils.Service;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CongestionControlFilterTest {

    private CongestionControlFilter<String, String, String> filter = new CongestionControlFilter<>(2, r -> r.substring(0, 2));
    private Service<String, String> service = mock(Service.class);

    @BeforeEach
    void setUp() {
        reset(service);
        given(service.apply(any())).willReturn(new CompletableFuture<>());
    }

    @Test
    void shouldFailWhenTooManyOutstandingInteractions() {
        // given
        CompletableFuture<String> resp = filter.apply("01:req1", service);
        CompletableFuture<String> resp2 = filter.apply("01:req2", service);

        // when
        CompletableFuture<String> resp3 = filter.apply("01:req2", service);

        // then
        assertTrue(resp3.isCompletedExceptionally());
        assertThatThrownBy(resp3::join).hasCauseExactlyInstanceOf(TooManyRequestsForEndpointException.class);

        assertFalse(resp.isDone());
        assertFalse(resp2.isDone());

        verify(service).apply(any());
    }

    @Test
    void shouldSendRequestToDifferentDestinations() {
        // when
        CompletableFuture<String> resp = filter.apply("01:req1", service);
        CompletableFuture<String> resp2 = filter.apply("02:req1", service);
        CompletableFuture<String> resp3 = filter.apply("03:req1", service);

        // then
        assertFalse(resp.isDone());
        assertFalse(resp2.isDone());
        assertFalse(resp3.isDone());
        verify(service, times(3)).apply(any());
    }

    @Test
    void shouldSendAfterPreviousIsDone() {
        given(service.apply(any())).willReturn(completedFuture("ok"));

        // when
        CompletableFuture<String> resp = filter.apply("01:req1", service);
        CompletableFuture<String> resp2 = filter.apply("01:req2", service);
        CompletableFuture<String> resp3 = filter.apply("01:req3", service);

        // then
        assertEquals("ok", resp.join());
        assertEquals("ok", resp2.join());
        assertEquals("ok", resp3.join());

        verify(service, times(3)).apply(any());
    }

}