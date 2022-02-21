/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
 * Copyright (C) 2011-2021 ARM Limited. All rights reserved.
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
import static com.mbed.coap.utils.Bytes.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.CoapBlockException;
import com.mbed.coap.exception.CoapBlockTooLargeEntityException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.utils.Service;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import protocolTests.utils.CoapPacketBuilder;

public class BlockWiseCallbackTest {

    private CompletableFuture<CoapPacket> promise;
    private CoapPacket lastReq;
    private final Service<CoapPacket, CoapPacket> makeRequestFunc = req -> {
        lastReq = req;
        promise = new CompletableFuture<>();
        return promise;
    };
    private CompletableFuture<CoapPacket> callback;
    private BlockWiseCallback bwc;

    @BeforeEach
    void setUp() {
        callback = new CompletableFuture<>();
    }

    @Test
    public void should_send_payload_in_blocks() throws CoapException {
        bwc = new BlockWiseCallback(makeRequestFunc, new CoapTcpCSM(512, true),
                coap().payload(opaqueOfSize(1500)).put().build(), 10_000);

        //BLOCK 1
        assertEquals(coap().block1Req(0, S_512, true).size1(1500).payload(opaqueOfSize(512)).put().build(), bwc.request);

        //when
        receiveFirst(coap().block1Req(0, S_512, false).ack(C231_CONTINUE));
        //then
        assertSent(coap().block1Req(1, S_512, true).payload(opaqueOfSize(512)).put());

        //BLOCK 2
        //when
        receive(coap().block1Req(1, S_512, false).ack(C231_CONTINUE));
        //then
        assertSent(coap().block1Req(2, S_512, false).payload(opaqueOfSize(476)).put());

        //when
        receive(coap().block1Req(2, S_512, false).ack(C204_CHANGED));
        //then
        assertEquals(coap().block1Req(2, S_512, false).ack(C204_CHANGED).build(), callback.join());
    }

    @Test
    public void should_receive_non_block_response() throws CoapException {
        givenPutRequest(100);
        assertEquals(coap().payload(opaqueOfSize(100)).put().build(), bwc.request);

        //when
        receiveFirst(coap().payload(opaqueOfSize(200)).ack(C204_CHANGED));

        //then
        assertEquals(coap().payload(opaqueOfSize(200)).ack(C204_CHANGED).build(), callback.join());
    }

    @Test
    public void should_receive_payload_in_blocks() throws CoapException {
        givenGetRequest();

        //when
        receiveFirst(coap().block2Res(0, S_512, true).payload(opaqueOfSize(512)).ack(C205_CONTENT));
        //then
        assertSent(coap().block2Res(1, S_512, false).get());

        //BLOCK 2
        //when
        receive(coap().block2Res(1, S_512, true).payload(opaqueOfSize(512)));
        //then
        assertSent(coap().block2Res(2, S_512, false).get());

        //when
        receive(coap().block2Res(2, S_512, false).payload(opaqueOfSize(100)).ack(C205_CONTENT));
        //then
        assertEquals(coap().block2Res(2, S_512, false).payload(opaqueOfSize(1124)).ack(C205_CONTENT).build(), callback.join());
    }

    @Test
    public void should_send_payload_in_bert_blocks() throws CoapException {
        bwc = new BlockWiseCallback(makeRequestFunc, new CoapTcpCSM(3500, true),
                coap().payload(opaqueOfSize(4196)).put().build(), 10_000);

        assertEquals(coap().payload(opaqueOfSize(100)).block1Req(0, S_1024_BERT, true).size1(4196).payload(opaqueOfSize(2048)).put().build(), bwc.request);

        //when
        receiveFirst(coap().block1Req(0, S_1024_BERT, false).ack(C231_CONTINUE));
        //then
        assertSent(coap().block1Req(2, S_1024_BERT, true).payload(opaqueOfSize(2048)).put());

        //BLOCK 2
        //when
        receive(coap().block1Req(2, S_1024_BERT, false).ack(C231_CONTINUE));
        //then
        assertSent(coap().block1Req(4, S_1024_BERT, false).payload(opaqueOfSize(100)).put());

        //when
        receive(coap().block1Req(4, S_1024_BERT, false).ack(C204_CHANGED));
        //then
        assertEquals(coap().block1Req(4, S_1024_BERT, false).ack(C204_CHANGED).build(), callback.join());
    }

    @Test
    public void should_receive_payload_in_bert_blocks() throws CoapException {
        bwc = new BlockWiseCallback(makeRequestFunc, new CoapTcpCSM(4096, true),
                coap().get().build(), 10_000);

        assertEquals(coap().get().build(), bwc.request);

        //when
        receiveFirst(coap().block2Res(0, S_1024_BERT, true).payload(opaqueOfSize(2048)).ack(C205_CONTENT));
        //then
        assertSent(coap().block2Res(2, S_1024_BERT, false).get());

        //BLOCK 2
        //when
        receive(coap().block2Res(2, S_1024_BERT, true).payload(opaqueOfSize(2048)));
        //then
        assertSent(coap().block2Res(4, S_1024_BERT, false).get());

        //when
        receive(coap().block2Res(4, S_1024_BERT, false).payload(opaqueOfSize(100)).ack(C205_CONTENT));
        //then
        assertEquals(coap().block2Res(4, S_1024_BERT, false).payload(opaqueOfSize(4196)).ack(C205_CONTENT).build(), callback.join());
    }

    @Test
    public void should_fail_when_response_missing_231_continue() throws CoapException {
        givenPutRequest(1500);

        //when
        receiveFirst(coap().block1Req(0, S_512, false).ack(C400_BAD_REQUEST));

        //then
        assertNothingSent();
        assertEquals(coap().block1Req(0, S_512, false).ack(C400_BAD_REQUEST).build(), callback.join());
    }

    @Test
    public void should_restart_transfer_when_etag_changes() throws CoapException {
        givenGetRequest();

        receiveFirst(coap().etag(100).block2Res(0, S_512, true).payload(opaqueOfSize(512)).ack(C205_CONTENT));
        assertSent(coap().block2Res(1, S_512, false).get());

        //when, etag changes
        receive(coap().etag(200).block2Res(1, S_512, true).payload(opaqueOfSize(512)));

        //then, start from beginning
        assertSent(coap().block2Res(0, S_512, false).get());

        receive(coap().etag(200).block2Res(0, S_512, false).payload(opaqueOfSize(500)).ack(C205_CONTENT));
        assertEquals(coap().etag(200).block2Res(0, S_512, false).payload(opaqueOfSize(500)).ack(C205_CONTENT).build(), callback.join());
    }

    @Test
    public void should_fail_transfer_when_etag_changes_multiple_times() throws CoapException {
        givenGetRequest();

        receiveFirst(coap().block2Res(0, S_512, true).payload(opaqueOfSize(512)).ack(C205_CONTENT));
        assertSent(coap().block2Res(1, S_512, false).get());

        //when, etag changes three times
        //1st etag change
        receive(coap().etag(100).block2Res(1, S_512, true).payload(opaqueOfSize(512)));
        assertSent(coap().block2Res(0, S_512, false).get());

        receive(coap().etag(100).block2Res(0, S_512, true).payload(opaqueOfSize(512)));
        assertSent(coap().block2Res(1, S_512, false).get());

        //2nd etag change
        receive(coap().etag(200).block2Res(1, S_512, true).payload(opaqueOfSize(512)));
        assertSent(coap().block2Res(0, S_512, false).get());

        receive(coap().etag(200).block2Res(0, S_512, true).payload(opaqueOfSize(512)));
        assertSent(coap().block2Res(1, S_512, false).get());

        //3rd etag change
        receive(coap().etag(300).block2Res(1, S_512, true).payload(opaqueOfSize(512)));

        //then
        assertNothingSent();
        assertThatThrownBy(() -> callback.get()).hasCauseExactlyInstanceOf(CoapBlockException.class);
    }

    @Test
    public void should_fail_transfer_when_block_number_do_not_match() throws CoapException {
        givenGetRequest();

        receiveFirst(coap().block2Res(0, S_512, true).payload(opaqueOfSize(512)).ack(C205_CONTENT));
        assertSent(coap().block2Res(1, S_512, false).get());

        //when, block number mismatch
        receive(coap().block2Res(20, S_512, true).payload(opaqueOfSize(512)));

        //then, fail
        assertNothingSent();
        assertThatThrownBy(() -> callback.get()).hasCauseExactlyInstanceOf(CoapBlockException.class);
    }

    @Test
    public void should_fail_transfer_when_invalid_block_response() throws CoapException {
        givenGetRequest();

        receiveFirst(coap().block2Res(0, S_512, true).payload(opaqueOfSize(512)).ack(C205_CONTENT));
        assertSent(coap().block2Res(1, S_512, false).get());

        //when, invalid block response
        receive(coap().block2Res(1, S_512, true).payload(opaqueOfSize(400)));

        //then, fail
        assertNothingSent();
        assertThatThrownBy(() -> callback.get()).hasCauseExactlyInstanceOf(CoapBlockException.class);
    }

    @Test
    public void should_fail_transfer_when_invalid_last_block_response() throws CoapException {
        givenGetRequest();

        //when, invalid block response
        receiveFirst(coap().block2Res(0, S_512, false).payload(opaqueOfSize(513)).ack(C205_CONTENT));

        //then, fail
        assertNothingSent();
        assertThatThrownBy(() -> callback.get()).hasCauseExactlyInstanceOf(CoapBlockException.class);
    }

    @Test
    public void should_fail_transfer_when_too_large_total_response_payload() throws CoapException {
        bwc = new BlockWiseCallback(makeRequestFunc, new CoapTcpCSM(1024, true),
                coap().get().build(), 1000);

        receiveFirst(coap().block2Res(0, S_512, true).payload(opaqueOfSize(512)).ack(C205_CONTENT));
        assertSent(coap().block2Res(1, S_512, false).get());

        //when
        receive(coap().block2Res(1, S_512, true).payload(opaqueOfSize(512)));

        //then, fail
        assertNothingSent();
        assertThatThrownBy(() -> callback.get()).hasCauseExactlyInstanceOf(CoapBlockTooLargeEntityException.class);
    }

    @Test
    public void should_send_payload_in_blocks_with_block_size_negotiation() throws CoapException {
        givenPutRequest(1500);
        assertEquals(coap().block1Req(0, S_1024, true).size1(1500).payload(opaqueOfSize(1024)).put().build(), bwc.request);

        //when, received ACK with smaller block size
        receiveFirst(coap().block1Req(0, S_256, false).ack(C231_CONTINUE));
        //then
        assertSent(coap().block1Req(1, S_256, true).payload(opaqueOfSize(256)).put());
    }

    @Test
    public void should_adjust_request_block_size_after_413_response() throws CoapException {
        givenPutRequest(1500);

        //when, received ACK 4.13 with a new size hint
        receiveFirst(coap().ack(C413_REQUEST_ENTITY_TOO_LARGE).block1Req(0, S_256, true));
        //then, restart with new size
        assertSent(coap().block1Req(0, S_256, true).size1(1500).payload(opaqueOfSize(256)).put());

        //and continue
        receive(coap().ack(C231_CONTINUE).block1Req(0, S_256, true));
        assertSent(coap().block1Req(1, S_256, true).payload(opaqueOfSize(256)).put());
    }

    @Test
    public void should_fail_transfer_when_received_413_response_without_block_size_hint() throws CoapException {
        givenPutRequest(1500);

        //when, received ACK 4.13
        receiveFirst(coap().ack(C413_REQUEST_ENTITY_TOO_LARGE));
        //then, restart with new size
        assertNothingSent();
        assertEquals(coap().ack(C413_REQUEST_ENTITY_TOO_LARGE).build(), callback.join());
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

    @Test
    public void should_fail_when_too_large_payload() throws CoapException {
        assertThrows(CoapException.class, () ->
                new BlockWiseCallback(makeRequestFunc, new CoapTcpCSM(2000, false),
                        coap().payload(opaqueOfSize(2010)).put().build(), 10_000)
        );
    }

    private CoapPacketBuilder coap() {
        return newCoapPacket(LOCAL_5683);
    }

    private void assertSent(CoapPacketBuilder coapPacket) {
        assertFalse(callback.isDone());
        assertNotNull(lastReq);
        lastReq = null;
        assertEquals(coapPacket.build(), bwc.request);
    }

    private void assertNothingSent() {
        assertNull(lastReq);
    }

    private void receiveFirst(CoapPacketBuilder coapPacket) {
        callback = bwc.receive(coapPacket.build());
    }

    private void receive(CoapPacketBuilder coapPacket) {
        promise.complete(coapPacket.build());
    }

    private void givenPutRequest(int payloadSize) throws CoapException {
        bwc = new BlockWiseCallback(makeRequestFunc, new CoapTcpCSM(1024, true),
                coap().payload(opaqueOfSize(payloadSize)).put().build(), 10_000);
    }

    private void givenGetRequest() throws CoapException {
        bwc = new BlockWiseCallback(makeRequestFunc, new CoapTcpCSM(1024, true),
                coap().get().build(), 10_000);

        assertEquals(coap().get().build(), bwc.request);
    }


}
