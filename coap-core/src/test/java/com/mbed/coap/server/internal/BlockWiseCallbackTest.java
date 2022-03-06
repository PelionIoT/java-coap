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
import static com.mbed.coap.packet.CoapRequest.*;
import static com.mbed.coap.packet.CoapResponse.of;
import static com.mbed.coap.packet.CoapResponse.*;
import static com.mbed.coap.packet.Code.*;
import static com.mbed.coap.utils.Bytes.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.exception.CoapBlockException;
import com.mbed.coap.exception.CoapBlockTooLargeEntityException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.utils.Service;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BlockWiseCallbackTest {

    private CompletableFuture<CoapResponse> promise;
    private CoapRequest lastReq;
    private final Service<CoapRequest, CoapResponse> makeRequestFunc = req -> {
        lastReq = req;
        promise = new CompletableFuture<>();
        return promise;
    };
    private CompletableFuture<CoapResponse> response;
    private BlockWiseCallback bwc;

    @BeforeEach
    void setUp() {
        response = new CompletableFuture<>();
    }

    @Test
    public void should_send_payload_in_blocks() throws CoapException {
        bwc = new BlockWiseCallback(makeRequestFunc, new CoapTcpCSM(512, true),
                put("/9").payload(opaqueOfSize(1500)), 10_000);

        //BLOCK 1
        assertEquals(put("/9").block1Req(0, S_512, true).size1(1500).payload(opaqueOfSize(512)), bwc.request);

        //when
        receiveFirst(of(C231_CONTINUE).block1Req(0, S_512, false));
        //then
        assertSent(put("/9").block1Req(1, S_512, true).payload(opaqueOfSize(512)));

        //BLOCK 2
        //when
        receive(of(C231_CONTINUE).block1Req(1, S_512, false));
        //then
        assertSent(put("/9").block1Req(2, S_512, false).payload(opaqueOfSize(476)));

        //when
        receive(of(C204_CHANGED).block1Req(2, S_512, false));
        //then
        assertEquals(of(C204_CHANGED).block1Req(2, S_512, false), response.join());
    }

    @Test
    public void should_receive_non_block_response() throws CoapException {
        givenPutRequest(100);
        assertEquals(put("/9").payload(opaqueOfSize(100)), bwc.request);

        //when
        receiveFirst(of(C204_CHANGED).payload(opaqueOfSize(200)));

        //then
        assertEquals(of(C204_CHANGED).payload(opaqueOfSize(200)), response.join());
    }

    @Test
    public void should_receive_payload_in_blocks() throws CoapException {
        givenGetRequest();

        //when
        receiveFirst(ok(opaqueOfSize(512)).block2Res(0, S_512, true));
        //then
        assertSent(get("/9").block2Res(1, S_512, false));

        //BLOCK 2
        //when
        receive(ok(opaqueOfSize(512)).block2Res(1, S_512, true));
        //then
        assertSent(get("/9").block2Res(2, S_512, false));

        //when
        receive(ok(opaqueOfSize(100)).block2Res(2, S_512, false));
        //then
        assertEquals(ok(opaqueOfSize(1124)).block2Res(2, S_512, false), response.join());
    }

    @Test
    public void should_send_payload_in_bert_blocks() throws CoapException {
        bwc = new BlockWiseCallback(makeRequestFunc, new CoapTcpCSM(3500, true),
                put("/9").payload(opaqueOfSize(4196)), 10_000);

        assertEquals(put("/9").block1Req(0, S_1024_BERT, true).size1(4196).payload(opaqueOfSize(2048)), bwc.request);

        //when
        receiveFirst(of(C231_CONTINUE).block1Req(0, S_1024_BERT, false));
        //then
        assertSent(put("/9").block1Req(2, S_1024_BERT, true).payload(opaqueOfSize(2048)));

        //BLOCK 2
        //when
        receive(of(C231_CONTINUE).block1Req(2, S_1024_BERT, false));
        //then
        assertSent(put("/9").block1Req(4, S_1024_BERT, false).payload(opaqueOfSize(100)));

        //when
        receive(of(C204_CHANGED).block1Req(4, S_1024_BERT, false));
        //then
        assertEquals(of(C204_CHANGED).block1Req(4, S_1024_BERT, false), response.join());
    }

    @Test
    public void should_receive_payload_in_bert_blocks() throws CoapException {
        bwc = new BlockWiseCallback(makeRequestFunc, new CoapTcpCSM(4096, true), get("/9"), 10_000);

        assertEquals(get("/9"), bwc.request);

        //when
        receiveFirst(ok(opaqueOfSize(2048)).block2Res(0, S_1024_BERT, true));
        //then
        assertSent(get("/9").block2Res(2, S_1024_BERT, false));

        //BLOCK 2
        //when
        receive(ok(opaqueOfSize(2048)).block2Res(2, S_1024_BERT, true));
        //then
        assertSent(get("/9").block2Res(4, S_1024_BERT, false));

        //when
        receive(ok(opaqueOfSize(100)).block2Res(4, S_1024_BERT, false).payload(opaqueOfSize(100)));
        //then
        assertEquals(ok(opaqueOfSize(4196)).block2Res(4, S_1024_BERT, false), response.join());
    }

    @Test
    public void should_fail_when_response_missing_231_continue() throws CoapException {
        givenPutRequest(1500);

        //when
        receiveFirst(badRequest().block1Req(0, S_512, false));

        //then
        assertNothingSent();
        assertEquals(badRequest().block1Req(0, S_512, false), response.join());
    }

    @Test
    public void should_restart_transfer_when_etag_changes() throws CoapException {
        givenGetRequest();

        receiveFirst(ok(opaqueOfSize(512)).etag(Opaque.of("100")).block2Res(0, S_512, true));
        assertSent(get("/9").block2Res(1, S_512, false));

        //when, etag changes
        receive(ok(opaqueOfSize(512)).etag(Opaque.of("200")).block2Res(1, S_512, true));

        //then, start from beginning
        assertSent(get("/9").block2Res(0, S_512, false));

        receive(ok(opaqueOfSize(500)).etag(Opaque.of("200")).block2Res(0, S_512, false));
        assertEquals(ok(opaqueOfSize(500)).etag(Opaque.of("200")).block2Res(0, S_512, false), response.join());
    }

    @Test
    public void should_fail_transfer_when_etag_changes_multiple_times() throws CoapException {
        givenGetRequest();

        receiveFirst(ok(opaqueOfSize(512)).block2Res(0, S_512, true));
        assertSent(get("/9").block2Res(1, S_512, false));

        //when, etag changes three times
        //1st etag change
        receive(ok(opaqueOfSize(512)).etag(Opaque.of("100")).block2Res(1, S_512, true));
        assertSent(get("/9").block2Res(0, S_512, false));

        receive(ok(opaqueOfSize(512)).etag(Opaque.of("100")).block2Res(0, S_512, true));
        assertSent(get("/9").block2Res(1, S_512, false));

        //2nd etag change
        receive(ok(opaqueOfSize(512)).etag(Opaque.of("200")).block2Res(1, S_512, true));
        assertSent(get("/9").block2Res(0, S_512, false));

        receive(ok(opaqueOfSize(512)).etag(Opaque.of("200")).block2Res(0, S_512, true));
        assertSent(get("/9").block2Res(1, S_512, false));

        //3rd etag change
        receive(ok(opaqueOfSize(512)).etag(Opaque.of("300")).block2Res(1, S_512, true));

        //then
        assertNothingSent();
        assertThatThrownBy(() -> response.get()).hasCauseExactlyInstanceOf(CoapBlockException.class);
    }

    @Test
    public void should_fail_transfer_when_block_number_do_not_match() throws CoapException {
        givenGetRequest();

        receiveFirst(ok(opaqueOfSize(512)).block2Res(0, S_512, true));
        assertSent(get("/9").block2Res(1, S_512, false));

        //when, block number mismatch
        receive(ok(opaqueOfSize(512)).block2Res(20, S_512, true));

        //then, fail
        assertNothingSent();
        assertThatThrownBy(() -> response.get()).hasCauseExactlyInstanceOf(CoapBlockException.class);
    }

    @Test
    public void should_fail_transfer_when_invalid_block_response() throws CoapException {
        givenGetRequest();

        receiveFirst(ok(opaqueOfSize(512)).block2Res(0, S_512, true));
        assertSent(get("/9").block2Res(1, S_512, false));

        //when, invalid block response
        receive(ok(opaqueOfSize(400)).block2Res(1, S_512, true));

        //then, fail
        assertNothingSent();
        assertThatThrownBy(() -> response.get()).hasCauseExactlyInstanceOf(CoapBlockException.class);
    }

    @Test
    public void should_fail_transfer_when_invalid_last_block_response() throws CoapException {
        givenGetRequest();

        //when, invalid block response
        receiveFirst(ok(opaqueOfSize(513)).block2Res(0, S_512, false));

        //then, fail
        assertNothingSent();
        assertThatThrownBy(() -> response.get()).hasCauseExactlyInstanceOf(CoapBlockException.class);
    }

    @Test
    public void should_fail_transfer_when_too_large_total_response_payload() throws CoapException {
        bwc = new BlockWiseCallback(makeRequestFunc, new CoapTcpCSM(1024, true), get("/9"), 1000);

        receiveFirst(ok(opaqueOfSize(512)).block2Res(0, S_512, true));
        assertSent(get("/9").block2Res(1, S_512, false));

        //when
        receive(ok(opaqueOfSize(512)).block2Res(1, S_512, true));

        //then, fail
        assertNothingSent();
        assertThatThrownBy(() -> response.get()).hasCauseExactlyInstanceOf(CoapBlockTooLargeEntityException.class);
    }

    @Test
    public void should_send_payload_in_blocks_with_block_size_negotiation() throws CoapException {
        givenPutRequest(1500);
        assertEquals(put("/9").block1Req(0, S_1024, true).size1(1500).payload(opaqueOfSize(1024)), bwc.request);

        //when, received ACK with smaller block size
        receiveFirst(of(C231_CONTINUE).block1Req(0, S_256, false));
        //then
        assertSent(put("/9").block1Req(1, S_256, true).payload(opaqueOfSize(256)));
    }

    @Test
    public void should_adjust_request_block_size_after_413_response() throws CoapException {
        givenPutRequest(1500);

        //when, received ACK 4.13 with a new size hint
        receiveFirst(of(C413_REQUEST_ENTITY_TOO_LARGE).block1Req(0, S_256, true));
        //then, restart with new size
        assertSent(put("/9").block1Req(0, S_256, true).size1(1500).payload(opaqueOfSize(256)));

        //and continue
        receive(of(C231_CONTINUE).block1Req(0, S_256, true));
        assertSent(put("/9").block1Req(1, S_256, true).payload(opaqueOfSize(256)));
    }

    @Test
    public void should_fail_transfer_when_received_413_response_without_block_size_hint() throws CoapException {
        givenPutRequest(1500);

        //when, received ACK 4.13
        receiveFirst(of(C413_REQUEST_ENTITY_TOO_LARGE));
        //then, restart with new size
        assertNothingSent();
        assertEquals(of(C413_REQUEST_ENTITY_TOO_LARGE), response.join());
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
                        put("/9").payload(opaqueOfSize(2010)), 10_000)
        );
    }

    private void assertSent(CoapRequest expected) {
        assertFalse(response.isDone());
        assertNotNull(lastReq);
        lastReq = null;
        assertEquals(expected, bwc.request);
    }

    private void assertNothingSent() {
        assertNull(lastReq);
    }

    private void receiveFirst(CoapResponse resp) {
        response = bwc.receive(resp);
    }

    private void receive(CoapResponse resp) {
        promise.complete(resp);
    }

    private void givenPutRequest(int payloadSize) throws CoapException {
        bwc = new BlockWiseCallback(makeRequestFunc, new CoapTcpCSM(1024, true),
                put("/9").payload(opaqueOfSize(payloadSize)), 10_000);
    }

    private void givenGetRequest() throws CoapException {
        bwc = new BlockWiseCallback(makeRequestFunc, new CoapTcpCSM(1024, true), get("/9"), 10_000);

        assertEquals(get("/9"), bwc.request);
    }


}
