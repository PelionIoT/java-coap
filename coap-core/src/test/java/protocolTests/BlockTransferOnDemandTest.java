/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
 * Copyright (c) 2023 Izuma Networks. All rights reserved.
 * 
 * SPDX-License-Identifier: Apache-2.0
 * 
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

import static org.junit.Assert.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapExchange;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.transport.InMemoryCoapTransport;
import com.mbed.coap.utils.CoapResource;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


/**
 * Created by olesmi01 on 19.4.2016.
 * Block1 header option block transfer size limit tests.
 */
public class BlockTransferOnDemandTest {
    private static final int MAX_DATA = 32;

    private CoapServer server = null;
    private CoapClient client = null;

    @Before
    public void setUp() throws IOException {

        server = CoapServerBuilder.newBuilder()
                .maxIncomingBlockTransferSize(MAX_DATA)
                .blockSize(BlockSize.S_16)
                .transport(InMemoryCoapTransport.create(5683))
                .build();

        server.start();

        server.addRequestHandler("/man", new ManualBlockTransferCoapResource());

        client = CoapClientBuilder.newBuilder(5683)
                .maxIncomingBlockTransferSize(MAX_DATA)
                .transport(InMemoryCoapTransport.create())
                .build();
    }

    @After
    public void tearDown() {
        client.close();
        server.stop();
    }

    @Test
    public void onDemandTest() throws ExecutionException, InterruptedException, CoapException {
        CoapPacket resp = client.resource("/man").blockSize(BlockSize.S_16).sync().get();
        assertEquals("16B-of-data-here-plus-some", resp.getPayloadString());
        assertEquals(1, resp.headers().getBlock2Res().getNr());
    }

    private class ManualBlockTransferCoapResource extends CoapResource {

        @Override
        public void get(CoapExchange exchange) throws CoapCodeException {

            exchange.setResponseCode(Code.C205_CONTENT);

            int blockNr = exchange.getRequestHeaders().getBlock2Res() == null ? 0 : exchange.getRequestHeaders().getBlock2Res().getNr();

            if (blockNr == 0) {
                exchange.getResponse().setPayload("16B-of-data-here");
                exchange.getResponseHeaders().setBlock2Res(new BlockOption(0, BlockSize.S_16, true));
            } else {
                // last packet
                exchange.getResponse().setPayload("-plus-some");
                exchange.getResponseHeaders().setBlock2Res(new BlockOption(1, BlockSize.S_16, false));
            }

            exchange.sendResponse();
        }
    }
}

