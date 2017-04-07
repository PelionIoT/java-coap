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
package com.mbed.coap.server;

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.exception.ObservationNotEstablishedException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.DataConvertingUtility;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Method;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Callback;
import java.net.InetSocketAddress;
import java.util.Random;

/**
 * Implements CoAP observe mechanism for CoAP Server. (draft-ietf-core-observe-11)
 *
 * @author szymon
 */
public class CoapServerObserve extends CoapServerBlocks {

    private ObservationIDGenerator observationIDGenerator = new SimpleObservationIDGenerator();

    protected CoapServerObserve() {
        super();
    }

    @Override
    public byte[] observe(String uri, InetSocketAddress destination, final Callback<CoapPacket> respCallback, byte[] token, TransportContext transportContext) throws CoapException {
        CoapPacket request = new CoapPacket(Method.GET, MessageType.Confirmable, uri, destination);
        request.setToken(token);
        request.headers().setObserve(0);
        return observe(request, respCallback, transportContext);
    }

    @Override
    public byte[] observe(CoapPacket request, final Callback<CoapPacket> respCallback, TransportContext transportContext) throws CoapException {
        if (request.headers().getObserve() == null) {
            request.headers().setObserve(0);
        }
        if (request.getToken() == null || request.getToken() == CoapPacket.DEFAULT_TOKEN) {
            request.setToken(observationIDGenerator.nextObservationID(request.headers().getUriPath()));
        }
        makeRequest(request, new Callback<CoapPacket>() {

            @Override
            public void callException(Exception ex) {
                respCallback.callException(ex);
            }

            @Override
            public void call(CoapPacket resp) {
                if (resp.getCode() == Code.C205_CONTENT && resp.headers().getObserve() == null) {
                    respCallback.callException(new ObservationNotEstablishedException(resp));
                    return;
                }
                respCallback.call(resp);
            }
        }, transportContext);
        return request.getToken();
    }

    /**
     * Sets observation id generator instance.
     *
     * @param observationIDGenerator observation id generator instance
     */
    public void setObservationIDGenerator(ObservationIDGenerator observationIDGenerator) {
        this.observationIDGenerator = observationIDGenerator;

    }

    /**
     * Interface for generating observation IDs.
     */
    public interface ObservationIDGenerator {

        /**
         * Returns next observation id.
         *
         * @param uri URI path
         * @return observation id
         */
        byte[] nextObservationID(String uri);
    }

    /**
     * Provides simple implementation of {@link ObservationIDGenerator}
     */
    public static class SimpleObservationIDGenerator implements ObservationIDGenerator {

        private long token;

        public SimpleObservationIDGenerator() {
            token = 0xFFFF & (new Random()).nextLong();
        }

        public SimpleObservationIDGenerator(int initToken) {
            token = initToken;
        }

        @Override
        public synchronized byte[] nextObservationID(String uri) {
            return DataConvertingUtility.convertVariableUInt(++token);
        }
    }
}
