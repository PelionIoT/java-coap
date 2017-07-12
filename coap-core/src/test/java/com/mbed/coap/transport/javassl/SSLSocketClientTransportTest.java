/**
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
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
package com.mbed.coap.transport.javassl;

import static org.junit.Assert.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.server.CoapServer;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import javax.net.ssl.SSLContext;
import org.junit.Test;

public class SSLSocketClientTransportTest {

    public static final char[] SECRET = "secret".toCharArray();
    private static KeyStore SRV_KS = SSLUtils.ksFrom("/test-server.jks", SECRET);
    private static KeyStore CLI_KS = SSLUtils.ksFrom("/test-client.jks", SECRET);


    @Test
    public void successfulConnection() throws Exception {

        SSLContext srvSslContext = SSLUtils.sslContext(SRV_KS, SECRET);
        SSLContext clientSslContext = SSLUtils.sslContext(CLI_KS, SECRET);
        CoapServer srv = CoapServer.builder()
                .transport(new SingleConnectionSSLSocketServerTransport(srvSslContext, 0, false))
                .build().start();


        InetSocketAddress serverAdr = new InetSocketAddress("localhost", srv.getLocalSocketAddress().getPort());
        CoapClient client = CoapClientBuilder.clientFor(serverAdr,
                CoapServer.builder().transport(new SSLSocketClientTransport(serverAdr, clientSslContext.getSocketFactory(), false)).build().start()
        );

        //        assertNotNull(client.ping().get());
        assertNotNull(client.resource("/test").get().get());


        client.close();
        srv.stop();

    }
}