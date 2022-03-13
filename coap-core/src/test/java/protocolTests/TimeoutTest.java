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
package protocolTests;

import static com.mbed.coap.packet.CoapRequest.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.exception.CoapTimeoutException;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.transmission.SingleTimeout;
import com.mbed.coap.transport.InMemoryCoapTransport;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;


public class TimeoutTest {

    @Test()
    public void testTimeout() throws IOException, CoapException {
        CoapClient client = CoapClientBuilder.newBuilder(InMemoryCoapTransport.createAddress(0))
                .transport(InMemoryCoapTransport.create())
                .timeout(new SingleTimeout(100))
                .build();

        assertThrows(CoapTimeoutException.class, () ->
                client.sendSync(get("/non/existing"))
        );

    }

    @Test
    public void timeoutTest() throws Exception {
        CoapServer cnn = CoapServerBuilder.newBuilder().transport(InMemoryCoapTransport.create()).timeout(new SingleTimeout(100)).build();
        cnn.start();

        CoapRequest request = get(InMemoryCoapTransport.createAddress(0), "/test/1");

        CompletableFuture<CoapResponse> callback = cnn.clientService().apply(request);

        //assertEquals("Wrong number of transactions", 1, cnn.getNumberOfTransactions());
        assertThatThrownBy(callback::get)
                .hasCauseExactlyInstanceOf(CoapTimeoutException.class);
        cnn.stop();

    }
}
