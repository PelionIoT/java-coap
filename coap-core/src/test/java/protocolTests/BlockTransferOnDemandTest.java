/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
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
package protocolTests;

import static com.mbed.coap.packet.CoapRequest.get;
import static com.mbed.coap.packet.Opaque.of;
import static com.mbed.coap.utils.Networks.localhost;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.RouterService;
import com.mbed.coap.transport.InMemoryCoapTransport;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


/**
 * Block1 header option block transfer size limit tests.
 */
public class BlockTransferOnDemandTest {
    private static final int MAX_DATA = 32;

    private CoapServer server = null;
    private CoapClient client = null;

    @BeforeEach
    public void setUp() throws IOException {

        server = CoapServer.builder()
                .maxIncomingBlockTransferSize(MAX_DATA)
                .blockSize(BlockSize.S_16)
                .transport(InMemoryCoapTransport.create(5683))
                .route(RouterService.builder()
                        .get("/man", new ManualBlockTransferCoapResource())
                        .build()
                )
                .build();

        server.start();

        client = CoapServer.builder()
                .maxIncomingBlockTransferSize(MAX_DATA)
                .transport(InMemoryCoapTransport.create())
                .buildClient(localhost(5683));
    }

    @AfterEach
    public void tearDown() throws IOException {
        client.close();
        server.stop();
    }

    @Test
    public void onDemandTest() throws ExecutionException, InterruptedException, CoapException {
        CoapResponse resp = client.sendSync(get("/man").blockSize(BlockSize.S_16));
        assertEquals("16B-of-data-here-plus-some", resp.getPayloadString());
        assertEquals(1, resp.options().getBlock2Res().getNr());
    }

    private class ManualBlockTransferCoapResource implements Service<CoapRequest, CoapResponse> {

        @Override
        public CompletableFuture<CoapResponse> apply(CoapRequest req) {
            int blockNr = req.options().getBlock2Res() == null ? 0 : req.options().getBlock2Res().getNr();

            if (blockNr == 0) {
                return completedFuture(new CoapResponse(Code.C205_CONTENT, of("16B-of-data-here"), opts ->
                        opts.setBlock2Res(new BlockOption(0, BlockSize.S_16, true))
                ));
            } else {
                // last packet
                return completedFuture(new CoapResponse(Code.C205_CONTENT, of("-plus-some"), opts ->
                        opts.setBlock2Res(new BlockOption(1, BlockSize.S_16, false))
                ));
            }

        }
    }
}

