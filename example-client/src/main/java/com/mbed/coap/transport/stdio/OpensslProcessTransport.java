/*
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
package com.mbed.coap.transport.stdio;

import com.mbed.coap.transport.javassl.CoapSerializer;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by szymon
 */
public class OpensslProcessTransport extends StreamBlockingTransport {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpensslProcessTransport.class);
    private final Process process;

    public OpensslProcessTransport(Process process, InetSocketAddress destination, CoapSerializer coapSerializer) {
        super(process.getOutputStream(), process.getInputStream(), destination, coapSerializer);
        this.process = process;
    }

    @Override
    public void stop() {
        process.destroy();
    }

    public static ProcessBuilder createProcess(String certPemFile, InetSocketAddress destination, boolean isDtls, String cipherSuite) throws IOException {
        String opensslBinPath = Optional.ofNullable(System.getenv("OPENSSL_BIN_PATH")).map(p -> p + "/").orElse("");

        InetSocketAddress adr = InetSocketAddress.createUnresolved(destination.getHostName(), destination.getPort());
        String sessIn = new File("openssl-session.tmp").exists() ? "-sess_in openssl-session.tmp " : "";
        String dtls = isDtls ? "-dtls " : "";

        String cmd = String.format("%sopenssl s_client -crlf -ign_eof -connect %s -cert %s -cipher %s %s -sess_out openssl-session.tmp %s -quiet",
                opensslBinPath, adr, certPemFile, cipherSuite, sessIn, dtls)
                .replace("  ", " ");

        LOGGER.info("Running " + cmd);

        return new ProcessBuilder(cmd.split(" "))
                .redirectError(ProcessBuilder.Redirect.INHERIT);
    }

}
