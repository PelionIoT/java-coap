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
package com.mbed.coap.cli;

import static com.mbed.coap.cli.TransportOptions.addressFromUri;
import com.mbed.coap.CoapConstants;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.MediaTypes;
import com.mbed.coap.packet.Method;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.filter.TokenGeneratorFilter;
import java.net.URI;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;


@Command(name = "send", mixinStandardHelpOptions = true, description = "Send CoAP requests", usageHelpAutoWidth = true)
public class SendCommand implements Callable<Integer> {

    CoapRequest request;
    @Spec private CommandLine.Model.CommandSpec spec;

    @Parameters(index = "0", description = "Method: GET | POST | PUT | DELETE")
    private Method method;

    @Parameters(index = "1", description = "Url: <scheme>://<host>:<port>/<uri-path>")
    private URI uri;

    @Parameters(index = "2", defaultValue = "", description = "Payload")
    private String payload;

    @Option(names = {"-b"}, description = "Block size, one of: 16, 32, 64, 128, 256, 512, 1024")
    private BlockSize blockSize;

    @Option(names = {"--proxy-uri"}, paramLabel = "<uri>", description = "Proxy-Uri")
    private String proxyUri;

    @Option(names = {"--content-format", "-c"}, paramLabel = "<content-format>", description = "Content format, for example: 50 (json), 40 (link-format), 0 (text-plain)")
    private Short contentFormat;

    @Option(names = {"--accept", "-a"}, paramLabel = "<accept>", description = "Content format we want to receive, for example: 60 (cbor), 50 (json), 0 (text-plain)")
    private Short accept;

    @Mixin
    private TransportOptions transportOptions;

    @Override
    public Integer call() throws Exception {

        CoapServer cliServer = transportOptions.create(uri,
                udpBuilder -> udpBuilder.blockSize(blockSize).outboundFilter(TokenGeneratorFilter.RANDOM).build(),
                tcpBuilder -> tcpBuilder.blockSize(blockSize).outboundFilter(TokenGeneratorFilter.RANDOM).build()
        ).start();

        try (CoapClient cli = CoapClient.create(addressFromUri(uri), cliServer)) {
            Thread.sleep(200);

            String uriPath = uri.getPath().isEmpty() ? CoapConstants.WELL_KNOWN_CORE : uri.getPath();
            request = CoapRequest.of(null, method, uriPath)
                    .query(uri.getQuery() == null ? "" : uri.getQuery())
                    .blockSize(blockSize)
                    .payload(payload)
                    .options(o -> {
                        o.setContentFormat(contentFormat);
                        o.setProxyUri(proxyUri);
                        if (accept != null) {
                            o.setAccept(accept);
                        }
                    });
            CoapResponse resp = cli.sendSync(request);

            if (resp.getPayload().nonEmpty()) {
                spec.commandLine().getOut().println();
                if (resp.options().getContentFormat() != null && (resp.options().getContentFormat() == MediaTypes.CT_APPLICATION_CBOR || resp.options().getContentFormat() == MediaTypes.CT_APPLICATION_OCTET__STREAM)) {
                    spec.commandLine().getOut().println(resp.getPayload().toHex());
                } else {
                    spec.commandLine().getOut().println(resp.getPayloadString());
                }
            }
        }
        return 0;
    }
}
