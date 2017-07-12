/**
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
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
package com.mbed.coap.server.internal;

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.*;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.reset;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.ReadOnlyCoapResource;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import protocolTests.utils.CoapPacketBuilder;


/*
    draft-ietf-core-coap-tcp-tls-09
 */
/*
    TODO:
    - send request with blocks
    - receive response with blocks
    - receive request with blocks
    - send response with blocks
    - [do we need it] custody option https://tools.ietf.org/html/draft-ietf-core-coap-tcp-tls-09#section-5.4.1
    - [do we need it] release messages https://tools.ietf.org/html/draft-ietf-core-coap-tcp-tls-09#section-5.5
 */
public class CoapServerForTcpTest {

    private final CoapTransport coapTransport = mock(CoapTransport.class);
    CoapServerForTcp server = new CoapServerForTcp(coapTransport);

    @Before
    public void setUp() throws Exception {
        server.start();
        given(coapTransport.sendPacket(any(), any(), any())).willReturn(CompletableFuture.completedFuture(true));
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
    }

    @Test
    public void should_receive_response_to_request() throws Exception {
        CompletableFuture<CoapPacket> resp = server.makeRequest(newCoapPacket(LOCAL_1_5683).token(2001).con().get().uriPath("/test").build());

        receive(newCoapPacket(LOCAL_1_5683).token(2001).ack(Code.C205_CONTENT));
        assertNotNull(resp.get());
    }

    @Test
    public void should_ignore_non_matching_response() throws Exception {
        CompletableFuture<CoapPacket> resp = server.makeRequest(newCoapPacket(LOCAL_1_5683).token(2001).con().get().uriPath("/test").build());

        receive(newCoapPacket(LOCAL_1_5683).token(1002).ack(Code.C205_CONTENT));
        assertFalse(resp.isDone());
    }

    @Test
    public void should_response_404_for_unknown_resource() throws Exception {
        receive(newCoapPacket(LOCAL_1_5683).token(2002).get().uriPath("/non-existing"));

        assertSent(newCoapPacket(LOCAL_1_5683).token(2002).ack(Code.C404_NOT_FOUND));
    }

    @Test
    public void should_response_205_to_existing_resource() throws Exception {
        server.addRequestHandler("/test", new ReadOnlyCoapResource("12345"));

        receive(newCoapPacket(LOCAL_1_5683).token(2003).get().uriPath("/test"));

        assertSent(newCoapPacket(LOCAL_1_5683).token(2003).ack(Code.C205_CONTENT).payload("12345"));
    }

    @Test
    public void should_fail_to_make_request_when_transport_fails() throws Exception {
        given(coapTransport.sendPacket(any(), any(), any())).willReturn(completedFuture(new IOException()));

        CompletableFuture<CoapPacket> resp = server.makeRequest(newCoapPacket(LOCAL_1_5683).token(2001).con().get().uriPath("/test").build());

        assertTrue(resp.isCompletedExceptionally());
    }

    @Test
    public void should_override_methods_not_relevant_in_reliable_transport() throws Exception {
        assertEquals(0, server.getNextMID());
        assertEquals(0, server.getNextMID());
        assertEquals(0, server.getNextMID());
    }

    //https://tools.ietf.org/html/draft-ietf-core-coap-tcp-tls-09#section-5.4
    @Test
    public void should_not_reply_to_empty_message() throws Exception {
        receive(newCoapPacket(LOCAL_1_5683));

        assertNothingSent();
    }

    @Test
    public void should_replay_pong_to_ping() throws Exception {
        receive(newCoapPacket(LOCAL_1_5683).token(2004).code(Code.C702_PING));

        assertSent(newCoapPacket(LOCAL_1_5683).token(2004).ack(Code.C703_PONG));
    }

    @Test
    public void should_call_exception_when_disconnected() throws Exception {
        CompletableFuture<CoapPacket> resp1 = server.makeRequest(newCoapPacket(LOCAL_1_5683).token(2001).con().get().uriPath("/test").build());
        CompletableFuture<CoapPacket> resp2 = server.makeRequest(newCoapPacket(LOCAL_1_5683).token(2002).con().get().uriPath("/test2").build());

        server.onDisconnected(LOCAL_1_5683);

        assertTrue(resp1.isCompletedExceptionally());
        assertThatThrownBy(resp1::get).hasCauseExactlyInstanceOf(IOException.class);
        assertTrue(resp2.isCompletedExceptionally());
        assertThatThrownBy(resp2::get).hasCauseExactlyInstanceOf(IOException.class);
    }

    @Test
    public void should_call_exception_when_abort_signal_received() throws Exception {
        CompletableFuture<CoapPacket> resp = server.makeRequest(newCoapPacket(LOCAL_1_5683).token(2001).con().get().uriPath("/test").build());
        reset(coapTransport);

        receive(newCoapPacket(LOCAL_1_5683).con(Code.C705_ABORT));

        assertTrue(resp.isCompletedExceptionally());
        assertThatThrownBy(resp::get).hasCauseExactlyInstanceOf(IOException.class);
        assertNothingSent();
    }

    //=======================================================================

    private static CompletableFuture<Boolean> completedFuture(IOException exception) {
        CompletableFuture f = new CompletableFuture();
        f.completeExceptionally(exception);
        return f;
    }

    private void receive(CoapPacketBuilder coapPacketBuilder) {
        server.handle(coapPacketBuilder.build(), TransportContext.NULL);
    }

    private void assertSent(CoapPacketBuilder coapPacketBuilder) throws CoapException, IOException {
        assertSent(coapPacketBuilder.build());
    }

    private void assertSent(CoapPacket coapPacket) throws CoapException, IOException {
        verify(coapTransport).sendPacket(eq(coapPacket), any(), any());
    }

    private void assertNothingSent() throws CoapException, IOException {
        verify(coapTransport, never()).sendPacket(any(), any(), any());
    }

}
