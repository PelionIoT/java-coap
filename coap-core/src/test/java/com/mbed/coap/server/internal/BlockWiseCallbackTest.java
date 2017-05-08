/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
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

import static com.mbed.coap.packet.BlockSize.*;
import static com.mbed.coap.packet.Code.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.CoapBlockException;
import com.mbed.coap.exception.CoapBlockTooLargeEntityException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.utils.RequestCallback;
import java.io.IOException;
import java8.util.function.Consumer;
import org.junit.Test;
import protocolTests.utils.CoapPacketBuilder;

public class BlockWiseCallbackTest {

    private final Consumer<BlockWiseCallback> makeRequestFunc = mock(Consumer.class);
    private final RequestCallback callback = mock(RequestCallback.class);
    private BlockWiseCallback bwc;

    @Test
    public void should_send_payload_in_blocks() throws CoapException {
        bwc = new BlockWiseCallback(makeRequestFunc, new CoapTcpCSM(512, true),
                coap().payload(new byte[1500]).put().build(),
                callback, 10_000);

        //BLOCK 1
        assertEquals(coap().block1Req(0, S_512, true).size1(1500).payload(new byte[512]).put().build(), bwc.request);

        //when
        receive(coap().block1Req(0, S_512, false).ack(C231_CONTINUE));
        //then
        assertSent(coap().block1Req(1, S_512, true).payload(new byte[512]).put());

        //BLOCK 2
        //when
        receive(coap().block1Req(1, S_512, false).ack(C231_CONTINUE));
        //then
        assertSent(coap().block1Req(2, S_512, false).payload(new byte[476]).put());

        //when
        receive(coap().block1Req(2, S_512, false).ack(C204_CHANGED));
        //then
        verify(callback).call(coap().block1Req(2, S_512, false).ack(C204_CHANGED).build());
    }

    @Test
    public void should_receive_non_block_response() throws CoapException {
        givenPutRequest(100);
        assertEquals(coap().payload(new byte[100]).put().build(), bwc.request);

        //when
        receive(coap().payload(new byte[200]).ack(C204_CHANGED));

        //then
        verify(callback).call(coap().payload(new byte[200]).ack(C204_CHANGED).build());
    }

    @Test
    public void should_receive_payload_in_blocks() throws CoapException {
        givenGetRequest();

        //when
        receive(coap().block2Res(0, S_512, true).payload(new byte[512]).ack(C205_CONTENT));
        //then
        assertSent(coap().block2Res(1, S_512, false).get());

        //BLOCK 2
        //when
        receive(coap().block2Res(1, S_512, true).payload(new byte[512]));
        //then
        assertSent(coap().block2Res(2, S_512, false).get());

        //when
        receive(coap().block2Res(2, S_512, false).payload(new byte[100]).ack(C205_CONTENT));
        //then
        verify(callback).call(coap().block2Res(2, S_512, false).payload(new byte[1124]).ack(C205_CONTENT).build());
    }

    @Test
    public void should_send_payload_in_bert_blocks() throws CoapException {
        bwc = new BlockWiseCallback(makeRequestFunc, new CoapTcpCSM(3500, true),
                coap().payload(new byte[4196]).put().build(),
                callback, 10_000);

        assertEquals(coap().payload(new byte[100]).block1Req(0, S_1024_BERT, true).size1(4196).payload(new byte[2048]).put().build(), bwc.request);

        //when
        receive(coap().block1Req(0, S_1024_BERT, false).ack(C231_CONTINUE));
        //then
        assertSent(coap().block1Req(2, S_1024_BERT, true).payload(new byte[2048]).put());

        //BLOCK 2
        //when
        receive(coap().block1Req(2, S_1024_BERT, false).ack(C231_CONTINUE));
        //then
        assertSent(coap().block1Req(4, S_1024_BERT, false).payload(new byte[100]).put());

        //when
        receive(coap().block1Req(4, S_1024_BERT, false).ack(C204_CHANGED));
        //then
        verify(callback).call(coap().block1Req(4, S_1024_BERT, false).ack(C204_CHANGED).build());
    }

    @Test
    public void should_receive_payload_in_bert_blocks() throws CoapException {
        bwc = new BlockWiseCallback(makeRequestFunc, new CoapTcpCSM(4096, true),
                coap().get().build(), callback, 10_000);

        assertEquals(coap().get().build(), bwc.request);

        //when
        receive(coap().block2Res(0, S_1024_BERT, true).payload(new byte[2048]).ack(C205_CONTENT));
        //then
        assertSent(coap().block2Res(2, S_1024_BERT, false).get());

        //BLOCK 2
        //when
        receive(coap().block2Res(2, S_1024_BERT, true).payload(new byte[2048]));
        //then
        assertSent(coap().block2Res(4, S_1024_BERT, false).get());

        //when
        receive(coap().block2Res(4, S_1024_BERT, false).payload(new byte[100]).ack(C205_CONTENT));
        //then
        verify(callback).call(coap().block2Res(4, S_1024_BERT, false).payload(new byte[4196]).ack(C205_CONTENT).build());
    }

    @Test
    public void should_fail_when_response_missing_231_continue() throws CoapException {
        givenPutRequest(1500);

        //when
        receive(coap().block1Req(0, S_512, false).ack(C400_BAD_REQUEST));

        //then
        assertNothingSent();
        verify(callback).call(coap().block1Req(0, S_512, false).ack(C400_BAD_REQUEST).build());
    }

    @Test
    public void should_restart_transfer_when_etag_changes() throws CoapException {
        givenGetRequest();

        receive(coap().etag(100).block2Res(0, S_512, true).payload(new byte[512]).ack(C205_CONTENT));
        assertSent(coap().block2Res(1, S_512, false).get());

        //when, etag changes
        receive(coap().etag(200).block2Res(1, S_512, true).payload(new byte[512]));

        //then, start from beginning
        assertSent(coap().block2Res(0, S_512, false).get());

        receive(coap().etag(200).block2Res(0, S_512, false).payload(new byte[500]).ack(C205_CONTENT));
        verify(callback).call(coap().etag(200).block2Res(0, S_512, false).payload(new byte[500]).ack(C205_CONTENT).build());
    }

    @Test
    public void should_fail_transfer_when_etag_changes_multiple_times() throws CoapException {
        givenGetRequest();

        receive(coap().block2Res(0, S_512, true).payload(new byte[512]).ack(C205_CONTENT));
        assertSent(coap().block2Res(1, S_512, false).get());

        //when, etag changes three times
        //1st etag change
        receive(coap().etag(100).block2Res(1, S_512, true).payload(new byte[512]));
        assertSent(coap().block2Res(0, S_512, false).get());

        receive(coap().etag(100).block2Res(0, S_512, true).payload(new byte[512]));
        assertSent(coap().block2Res(1, S_512, false).get());

        //2nd etag change
        receive(coap().etag(200).block2Res(1, S_512, true).payload(new byte[512]));
        assertSent(coap().block2Res(0, S_512, false).get());

        receive(coap().etag(200).block2Res(0, S_512, true).payload(new byte[512]));
        assertSent(coap().block2Res(1, S_512, false).get());

        //3rd etag change
        receive(coap().etag(300).block2Res(1, S_512, true).payload(new byte[512]));

        //then
        assertNothingSent();
        verify(callback).callException(isA(CoapBlockException.class));
    }

    @Test
    public void should_fail_transfer_when_block_number_do_not_match() throws CoapException {
        givenGetRequest();

        receive(coap().block2Res(0, S_512, true).payload(new byte[512]).ack(C205_CONTENT));
        assertSent(coap().block2Res(1, S_512, false).get());

        //when, block number mismatch
        receive(coap().block2Res(20, S_512, true).payload(new byte[512]));

        //then, fail
        assertNothingSent();
        verify(callback).callException(isA(CoapBlockException.class));
    }

    @Test
    public void should_fail_transfer_when_invalid_block_response() throws CoapException {
        givenGetRequest();

        receive(coap().block2Res(0, S_512, true).payload(new byte[512]).ack(C205_CONTENT));
        assertSent(coap().block2Res(1, S_512, false).get());

        //when, invalid block response
        receive(coap().block2Res(1, S_512, true).payload(new byte[400]));

        //then, fail
        assertNothingSent();
        verify(callback).callException(isA(CoapBlockException.class));
    }

    @Test
    public void should_fail_transfer_when_invalid_last_block_response() throws CoapException {
        givenGetRequest();

        //when, invalid block response
        receive(coap().block2Res(0, S_512, false).payload(new byte[513]).ack(C205_CONTENT));

        //then, fail
        assertNothingSent();
        verify(callback).callException(isA(CoapBlockException.class));
    }

    @Test
    public void should_fail_transfer_when_too_large_total_response_payload() throws CoapException {
        bwc = new BlockWiseCallback(makeRequestFunc, new CoapTcpCSM(1024, true),
                coap().get().build(), callback, 1000);

        receive(coap().block2Res(0, S_512, true).payload(new byte[512]).ack(C205_CONTENT));
        assertSent(coap().block2Res(1, S_512, false).get());

        //when
        receive(coap().block2Res(1, S_512, true).payload(new byte[512]));

        //then, fail
        assertNothingSent();
        verify(callback).callException(isA(CoapBlockTooLargeEntityException.class));
    }

    @Test
    public void should_send_payload_in_blocks_with_block_size_negotiation() throws CoapException {
        givenPutRequest(1500);
        assertEquals(coap().block1Req(0, S_1024, true).size1(1500).payload(new byte[1024]).put().build(), bwc.request);

        //when, received ACK with smaller block size
        receive(coap().block1Req(0, S_256, false).ack(C231_CONTINUE));
        //then
        assertSent(coap().block1Req(1, S_256, true).payload(new byte[256]).put());
    }

    @Test
    public void should_adjust_request_block_size_after_413_response() throws CoapException {
        givenPutRequest(1500);

        //when, received ACK 4.13 with a new size hint
        receive(coap().ack(C413_REQUEST_ENTITY_TOO_LARGE).block1Req(0, S_256, true));
        //then, restart with new size
        assertSent(coap().block1Req(0, S_256, true).size1(1500).payload(new byte[256]).put());

        //and continue
        receive(coap().ack(C231_CONTINUE).block1Req(0, S_256, true));
        assertSent(coap().block1Req(1, S_256, true).payload(new byte[256]).put());
    }

    @Test
    public void should_fail_transfer_when_received_413_response_without_block_size_hint() throws CoapException {
        givenPutRequest(1500);

        //when, received ACK 4.13
        receive(coap().ack(C413_REQUEST_ENTITY_TOO_LARGE));
        //then, restart with new size
        assertNothingSent();
        verify(callback).call(eq(coap().ack(C413_REQUEST_ENTITY_TOO_LARGE).build()));
    }

    @Test
    public void should_forward_wrapped_method_calls() throws CoapException {
        givenGetRequest();

        bwc.onSent();
        verify(callback).onSent();

        bwc.callException(new IOException());
        verify(callback).callException(isA(IOException.class));
    }

    @Test
    public void should_create_next_bert_block_option() {

        assertEquals(new BlockOption(2, S_512, true),
                BlockWiseCallback.nextBertBlock(new BlockOption(1, S_512, true), 2000, 1, 1000));

        assertEquals(new BlockOption(4, S_512, false),
                BlockWiseCallback.nextBertBlock(new BlockOption(3, S_512, true), 2000, 1, 1000));

        //BERT
        assertEquals(new BlockOption(2, S_1024_BERT, true),
                BlockWiseCallback.nextBertBlock(new BlockOption(0, S_1024_BERT, true), 10000, 2, 3000));

    }

    @Test(expected = CoapException.class)
    public void should_fail_when_too_large_payload() throws CoapException {
        new BlockWiseCallback(makeRequestFunc, new CoapTcpCSM(2000, false),
                coap().payload(new byte[2010]).put().build(), callback, 10_000);
    }

    private CoapPacketBuilder coap() {
        return newCoapPacket(LOCAL_5683);
    }

    private void assertSent(CoapPacketBuilder coapPacket) {
        verify(callback, never()).call(any());
        verify(callback, never()).callException(any());
        verify(makeRequestFunc).accept(any());
        reset(makeRequestFunc);
        assertEquals(coapPacket.build(), bwc.request);
    }

    private void assertNothingSent() {
        verify(makeRequestFunc, never()).accept(any());
    }

    private void receive(CoapPacketBuilder coapPacket) {
        bwc.call(coapPacket.build());
    }

    private void givenPutRequest(int payloadSize) throws CoapException {
        bwc = new BlockWiseCallback(makeRequestFunc, new CoapTcpCSM(1024, true),
                coap().payload(new byte[payloadSize]).put().build(),
                callback, 10_000);
    }

    private void givenGetRequest() throws CoapException {
        bwc = new BlockWiseCallback(makeRequestFunc, new CoapTcpCSM(1024, true),
                coap().get().build(), callback, 10_000);

        assertEquals(coap().get().build(), bwc.request);
    }


}