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
package com.mbed.coap.server.block;

import static com.mbed.coap.packet.BlockSize.*;
import static com.mbed.coap.packet.CoapResponse.*;
import static java.util.concurrent.CompletableFuture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.packet.SeparateResponse;
import com.mbed.coap.server.messaging.CoapTcpCSM;
import com.mbed.coap.server.messaging.CoapTcpCSMStorage;
import com.mbed.coap.server.messaging.CoapTcpCSMStorageImpl;
import com.mbed.coap.utils.Service;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BlockWiseNotificationFilterTest {

    private final CoapTcpCSMStorage csmStorage = new CoapTcpCSMStorageImpl();
    private final BlockWiseNotificationFilter filter = new BlockWiseNotificationFilter(csmStorage);
    private final Service<SeparateResponse, Boolean> service = Mockito.mock(Service.class);
    private final Opaque token = Opaque.of("1");

    @BeforeEach
    void setUp() {
        reset(service);
        given(service.apply(any())).willReturn(completedFuture(true));

        csmStorage.put(LOCAL_1_5683, new CoapTcpCSM(16, true));
    }

    @Test
    void shouldPassWhenSmallPayload() {
        // when
        CompletableFuture<Boolean> resp = filter.apply(ok("OK").toSeparate(token, LOCAL_1_5683), service);

        // then
        assertTrue(resp.join());
        verify(service).apply(ok("OK").toSeparate(token, LOCAL_1_5683));
    }

    @Test
    void shouldSendFirstBlockForLargePayload() {
        // when
        CompletableFuture<Boolean> resp = filter.apply(ok("aaaaaaaaaaaaaaabbbbbbbbbccc").toSeparate(token, LOCAL_1_5683), service);

        // then
        assertTrue(resp.join());
        SeparateResponse expected = ok("aaaaaaaaaaaaaaab").options(o -> o.setSize2Res(27)).block2Res(0, S_16, true).toSeparate(token, LOCAL_1_5683);
        verify(service).apply(expected);
    }
}