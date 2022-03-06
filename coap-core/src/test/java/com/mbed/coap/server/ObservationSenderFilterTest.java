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
package com.mbed.coap.server;

import static com.mbed.coap.packet.CoapRequest.*;
import static com.mbed.coap.packet.CoapResponse.*;
import static com.mbed.coap.packet.Opaque.of;
import static com.mbed.coap.packet.Opaque.*;
import static com.mbed.coap.utils.FutureHelpers.failedFuture;
import static java.util.concurrent.CompletableFuture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import com.mbed.coap.exception.CoapTimeoutException;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.packet.SeparateResponse;
import com.mbed.coap.utils.FutureQueue;
import com.mbed.coap.utils.Service;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ObservationSenderFilterTest {

    private Service<SeparateResponse, Boolean> sendNotification = Mockito.mock(Service.class);
    private ObservationSenderFilter obs = new ObservationSenderFilter(sendNotification);
    private FutureQueue<CoapResponse> next = null;

    @BeforeEach
    public void setUp() throws Exception {
        reset(sendNotification);

        given(sendNotification.apply(any())).willReturn(
                completedFuture(true)
        );
        next = new FutureQueue<>();
    }

    @AfterEach
    public void tearDown() throws Exception {
        verifyNoMoreInteractions(sendNotification);
    }

    @Test
    public void passResponseAndNoObservations() throws ExecutionException, InterruptedException {
        // when
        CompletableFuture<CoapResponse> resp = inServiceResponse(CoapResponse.ok("test"));

        assertEquals(CoapResponse.ok("test"), resp.get());
        verifyNoInteractions(sendNotification);
    }

    @Test
    public void sendObservations() {
        // given
        inServiceResponse(CoapResponse.ok("test").nextSupplier(next));

        // when
        assertTrue(next.put(new CoapResponse(Code.C205_CONTENT, of("test2"), opts -> opts.setObserve(2))));
        assertTrue(next.put(new CoapResponse(Code.C205_CONTENT, of("test3"), opts -> opts.setObserve(3))));
        assertTrue(next.put(null));

        // then
        SeparateResponse notif1 = ok("test2").observe(2).toSeparate(EMPTY, null);
        verify(sendNotification).apply(eq(notif1));
        SeparateResponse notif2 = ok("test3").observe(3).toSeparate(EMPTY, null);
        verify(sendNotification).apply(eq(notif2));
        assertNull(next.promise);
    }

    @Test
    public void clientTerminatesObservationWithReset() {
        // given
        inServiceResponse(CoapResponse.ok("test").nextSupplier(next));
        given(sendNotification.apply(any())).willReturn(
                completedFuture(false)
        );


        // when
        assertTrue(next.put(new CoapResponse(Code.C205_CONTENT, of("test2"), opts -> opts.setObserve(2))));

        // then
        assertFalse(next.put(new CoapResponse(Code.C205_CONTENT, of("test3"), opts -> opts.setObserve(3))));
        // and
        SeparateResponse notif1 = ok("test2").observe(2).toSeparate(EMPTY, null);
        verify(sendNotification).apply(eq(notif1));
        assertNull(next.promise);
    }

    @Test
    public void terminateWhenTimeoutException() {
        // given
        inServiceResponse(CoapResponse.ok("test").nextSupplier(next));
        given(sendNotification.apply(any())).willReturn(failedFuture(new CoapTimeoutException("")));


        // when
        assertTrue(next.put(new CoapResponse(Code.C205_CONTENT, of("test2"), opts -> opts.setObserve(2))));

        // then
        assertFalse(next.put(new CoapResponse(Code.C205_CONTENT, of("test3"), opts -> opts.setObserve(3))));
        // and
        SeparateResponse notif1 = ok("test2").observe(2).toSeparate(EMPTY, null);
        verify(sendNotification).apply(eq(notif1));
        assertNull(next.promise);
    }

    @Test
    public void terminateWhenErrorObservation() {
        // given
        inServiceResponse(CoapResponse.ok("test").nextSupplier(next));

        // when
        assertTrue(next.put(new CoapResponse(Code.C404_NOT_FOUND, of("test2"), opts -> opts.setObserve(2))));

        // then
        assertFalse(next.put(new CoapResponse(Code.C205_CONTENT, of("test3"), opts -> opts.setObserve(3))));
        // and
        SeparateResponse notif1 = notFound().payload(Opaque.of("test2")).observe(2).toSeparate(EMPTY, null);
        verify(sendNotification).apply(eq(notif1));
        assertNull(next.promise);

    }

    @Test
    public void terminateWhenCanceledObservation() {
        // given
        inServiceResponse(CoapResponse.ok("test").nextSupplier(next));

        // when
        assertTrue(next.cancel());

        // then
        verifyNoInteractions(sendNotification);
        assertNull(next.promise);
    }

    private CompletableFuture<CoapResponse> inServiceResponse(CoapResponse resp) {
        return obs.apply(get("/test"), __ -> completedFuture(resp));
    }


}
