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
package com.mbed.coap.transport.stdio;

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.transport.BlockingCoapTransport;
import com.mbed.coap.transport.CoapReceiver;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.transport.TransportExecutors;
import com.mbed.coap.transport.javassl.CoapSerializer;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by szymon
 */
public class StreamBlockingTransport extends BlockingCoapTransport {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamBlockingTransport.class);
    private final OutputStream outputStream;
    private final InputStream inputStream;
    protected final InetSocketAddress destination;
    private final Executor readingWorker = TransportExecutors.newWorker("stream-reader");
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
    public void sendPacket0(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) throws IOException, CoapException {
        serializer.serialize(outputStream, coapPacket);
        outputStream.flush();
    }

    @Override
    public void start(CoapReceiver coapReceiver) {
        isRunning = true;
        TransportExecutors.loop(readingWorker, () -> readingLoop(coapReceiver));
    }

    @Override
    public void stop() {
        isRunning = false;
        TransportExecutors.shutdown(readingWorker);
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return null;
    }

    private boolean readingLoop(CoapReceiver coapReceiver) {
        try {
            coapReceiver.handle(serializer.deserialize(inputStream, destination), TransportContext.NULL);
        } catch (InterruptedIOException e) {
            //IGNORE
            isRunning = false;
        } catch (Exception e) {
            LOGGER.error(e.toString(), e);
            isRunning = false;
        } finally {
            isRunning = false;
        }
        return isRunning;
    }

    boolean isRunning() {
        return isRunning;
    }
}
