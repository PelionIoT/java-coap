/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
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

import static com.mbed.coap.packet.BlockSize.S_16;
import static com.mbed.coap.packet.CoapResponse.ok;
import static com.mbed.coap.transport.udp.DatagramSocketTransport.udp;
import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MediaTypes;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.RouterService;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import picocli.CommandLine;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoapCliTest {

    private CoapServer stubServer;
    private int port;

    @BeforeAll
    void beforeAll() throws IOException {
        stubServer = CoapServer.builder()
                .transport(udp(0))
                .route(RouterService.builder()
                        .post("/rd", req -> {
                            String epName = req.options().getUriQueryMap().get("ep");
                            return completedFuture(new CoapResponse(Code.C201_CREATED, Opaque.EMPTY, o -> o.setLocationPath("/rd/" + epName)));
                        })
                        .get("/test", __ -> completedFuture(ok("Dziala!")))
                        .post("/test", req -> completedFuture(ok("Received " + req.getPayload().size())))

                )
                .build()
                .start();
        port = stubServer.getLocalSocketAddress().getPort();
    }

    private SendCommand sendCommand;
    private DeviceEmulator deviceEmulator;
    private CommandLine cmd;
    private StringWriter sw;

    @BeforeEach
    void setUp() {
        cmd = Main.createCommandLine();
        sw = new StringWriter();
        cmd.setOut(new PrintWriter(sw));
        sendCommand = cmd.getSubcommands().get("send").getCommand();
        deviceEmulator = cmd.getSubcommands().get("register").getCommand();
    }

    @AfterAll
    void afterAll() {
        stubServer.stop();
    }

    @Test
    void simplestRequest() {
        // when
        int exitCode = cmd.execute("send", "GET", format("coap://localhost:%d/test", port));

        // then
        assertEquals(0, exitCode);
        assertEquals("\nDziala!\n", sw.toString());
        assertEquals(CoapRequest.get("/test"), sendCommand.request);
    }

    @Test
    void requestWithAllCoapParameters() {
        // when
        int exitCode = cmd.execute(
                "send", "-b", "16", "--proxy-uri", "http://another-uri", "-c=50",
                "POST", "coap://localhost:" + port + "/test?par1=val1",
                "{\"id\":\"22da5c828e9c4f10bc57\"}"
        );

        // then
        assertEquals(0, exitCode);
        assertEquals("\nReceived 29\n", sw.toString());

        CoapRequest expected = CoapRequest.post("/test")
                .query("par1", "val1")
                .payload(Opaque.of("{\"id\":\"22da5c828e9c4f10bc57\"}"), MediaTypes.CT_APPLICATION_JSON)
                .block1Req(1, S_16, false)
                .proxy("http://another-uri");
        assertEquals(expected, sendCommand.request);
    }

    @Test
    public void registerEmulator() {
        int exitCode = cmd.execute("register", format("coap://localhost:%d/rd?ep=dev123&lt=60", port));

        assertEquals(-1, exitCode);
        await().untilAsserted(() ->
                assertTrue(deviceEmulator.registrationManager.isRegistered())
        );
    }

    @Test
    public void failToRegisterEmulator() {
        int exitCode = cmd.execute("register", format("coap://localhost:%d/non-existing?ep=dev123&lt=60", port));

        assertEquals(1, exitCode);
        assertFalse(deviceEmulator.registrationManager.isRegistered());
    }
}
