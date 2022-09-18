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
package com.mbed.coap.server.messaging;

import static com.mbed.coap.utils.Bytes.*;
import static java.util.concurrent.CompletableFuture.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.utils.Service;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class PayloadSizeVerifierTest {

    private final CoapTcpCSMStorageImpl csmStorage = new CoapTcpCSMStorageImpl();
    private PayloadSizeVerifier<Boolean> verifier = new PayloadSizeVerifier<>(csmStorage);
    private final Service<CoapPacket, Boolean> service = __ -> completedFuture(true);

    @Test
    public void shouldThrowExceptionWhenTooLargePayload() {
        CompletableFuture<Boolean> resp = verifier.apply(newCoapPacket(LOCAL_5683).post().uriPath("/").payload(opaqueOfSize(2000)).build(), service);

        assertTrue(resp.isCompletedExceptionally());
        assertThatThrownBy(resp::get).hasCauseExactlyInstanceOf(CoapException.class);
    }

    @Test
    public void shouldForwardWhenPayloadSizeIsOK() {
        csmStorage.put(LOCAL_5683, new CoapTcpCSM(2000, true));

        CompletableFuture<Boolean> resp = verifier.apply(newCoapPacket(LOCAL_5683).post().uriPath("/").payload(opaqueOfSize(2000)).build(), service);

        assertTrue(resp.join());
    }


}