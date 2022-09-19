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
package protocolTests;

import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.utils.Service;
import java.io.IOException;

public class UdpIntegrationTest extends IntegrationTestBase {

    @Override
    protected CoapClient buildClient(int port) throws IOException {
        return CoapClientBuilder.newBuilder(port).blockSize(BlockSize.S_1024).build();
    }

    @Override
    protected CoapServer buildServer(int port, Service<CoapRequest, CoapResponse> route) throws IOException {
        return CoapServerBuilder.newBuilder().blockSize(BlockSize.S_1024).transport(port).route(route).build();
    }

}
