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
import static com.mbed.coap.transmission.RetransmissionBackOff.ofFixed;
import static java.time.Duration.ofMillis;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.exception.CoapTimeoutException;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.transport.InMemoryCoapTransport;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;


public class TimeoutTest {

    @Test()
    public void testTimeout() throws IOException, CoapException {
        CoapClient client = CoapServer.builder()
                .transport(InMemoryCoapTransport.create())
                .retransmission(ofFixed(ofMillis(100)))
                .buildClient(InMemoryCoapTransport.createAddress(0));

        assertThrows(CoapTimeoutException.class, () ->
                client.sendSync(get("/non/existing"))
        );

    }

    @Test
    public void timeoutTest() throws Exception {
        CoapServer cnn = CoapServer.builder().transport(InMemoryCoapTransport.create()).retransmission(ofFixed(ofMillis(100))).build();
        cnn.start();

        CoapRequest request = get(InMemoryCoapTransport.createAddress(0), "/test/1");

        CompletableFuture<CoapResponse> callback = cnn.clientService().apply(request);

        //assertEquals("Wrong number of transactions", 1, cnn.getNumberOfTransactions());
        assertThatThrownBy(callback::get)
                .hasCauseExactlyInstanceOf(CoapTimeoutException.class);
        cnn.stop();

    }
}
