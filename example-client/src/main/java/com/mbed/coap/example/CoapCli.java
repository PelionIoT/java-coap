/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
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
package com.mbed.coap.example;

import com.mbed.coap.CoapConstants;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Method;
import com.mbed.coap.server.CoapServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;

/**
 * Created by szymon
 */
public class CoapCli {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: [options...] <method> coap://<host>:<port>/<uri-path> [<payload>]");
            System.out.println("Method: GET | POST | PUT | DELETE");
            System.out.println("Options:");
            System.out.println("     -k <file>          KeyStore file");
            System.out.println("     -b <block size>    Block size, one of: 16, 32, 64, 128, 256, 512, 1024");
            System.out.println("     -p <proxy>         Proxy-Uri");
            System.out.println("");
            System.out.println("Example: GET coap://localhost:5683/small");
            System.out.println("");
            return;
        }
        try {

            String keystoreFile = null;
            String proxyUri = null;
            BlockSize blockSize = null;
            int i;
            for (i = 0; i < args.length; i++) {
                if (args[i].equals("-k")) {
                    keystoreFile = args[++i];
                } else if (args[i].equals("-p")) {
                    proxyUri = args[++i];
                } else if (args[i].equals("-b")) {
                    blockSize = BlockSize.valueOf("S_" + args[++i]);
                } else {
                    break;
                }
            }

            String method = args[i++];
            URI uri = URI.create(args[i++]);

            String payload = (args.length > i) ? args[i] : null;

            new CoapCli(keystoreFile, blockSize, proxyUri, method, uri, payload);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        System.exit(0);
    }

    public CoapCli(String keystoreFile, BlockSize blockSize, String proxyUri, String method, URI uri, String payload) throws IOException, InterruptedException, CoapException {

        CoapServer cliServer = CliUtils.builderFrom(keystoreFile, uri).build().start();

        InetSocketAddress destination = new InetSocketAddress(uri.getHost(), uri.getPort());
        CoapClient cli = CoapClientBuilder.clientFor(destination, cliServer);

        Thread.sleep(200);

        String uriPath = uri.getPath().isEmpty() ? CoapConstants.WELL_KNOWN_CORE : uri.getPath();
        CoapPacket resp = cli.resource(uriPath)
                .proxy(proxyUri)
                .blockSize(blockSize)
                .payload(payload)
                .sync().invokeMethod(Method.valueOf(method));

        System.out.println("");
        System.out.println(resp.getPayloadString());

        cliServer.stop();
    }


}
