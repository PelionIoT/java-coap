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
package com.mbed.coap.transport.stdio;

import static java.util.concurrent.CompletableFuture.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.transport.BlockingCoapTransport;
import com.mbed.coap.transport.CoapTcpListener;
import com.mbed.coap.transport.CoapTcpTransport;
import com.mbed.coap.transport.javassl.CoapSerializer;
import com.mbed.coap.utils.ExecutorHelpers;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;


public class StreamBlockingTransport extends BlockingCoapTransport implements CoapTcpTransport {
    private final OutputStream outputStream;
    private final InputStream inputStream;
    protected final InetSocketAddress destination;
    private final ExecutorService readingWorker = ExecutorHelpers.newSingleThreadExecutor("stream-reader");
    private volatile Boolean isRunning = false;
    private final CoapSerializer serializer;

    /**
     * Transport that writes and reads from standard IO.
     *
     * Important! Logs must be written to std-err.
     */
    public static StreamBlockingTransport forStandardIO(InetSocketAddress destination, CoapSerializer serializer) {
        return new StreamBlockingTransport(System.out, System.in, destination, serializer);
    }

    public StreamBlockingTransport(OutputStream outputStream, InputStream inputStream, InetSocketAddress destination, CoapSerializer serializer) {
        this.outputStream = new BufferedOutputStream(outputStream);
        this.inputStream = inputStream;
        this.destination = destination;
        this.serializer = serializer;
    }

    @Override
    public void sendPacket0(CoapPacket coapPacket) throws IOException, CoapException {
        serializer.serialize(outputStream, coapPacket);
        outputStream.flush();
    }

    @Override
    public void start() {
        isRunning = true;
    }

    @Override
    public void stop() {
        isRunning = false;
        readingWorker.shutdown();
    }

    @Override
    public CompletableFuture<CoapPacket> receive() {
        return supplyAsync(this::read, readingWorker)
                .thenCompose(it -> (it == null) ? receive() : completedFuture(it));

    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return null;
    }

    private CoapPacket read() {
        try {
            return serializer.deserialize(inputStream, destination);
        } catch (CoapException | IOException e) {
            isRunning = false;
            throw new CompletionException(e);
        }
    }

    boolean isRunning() {
        return isRunning;
    }

    @Override
    public void setListener(CoapTcpListener listener) {
        // do nothing
    }
}
