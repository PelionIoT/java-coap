/**
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
package com.mbed.coap.server;

import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Filter;
import com.mbed.coap.utils.Service;
import java.net.InetSocketAddress;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObservationSenderFilter implements Filter.SimpleFilter<CoapRequest, CoapResponse> {
    private final static Logger LOGGER = LoggerFactory.getLogger(ObservationSenderFilter.class);

    private final BiFunction<CoapPacket, TransportContext, CompletableFuture<CoapPacket>> sendNotification;

    public ObservationSenderFilter(BiFunction<CoapPacket, TransportContext, CompletableFuture<CoapPacket>> sendNotification) {
        this.sendNotification = sendNotification;
    }

    @Override
    public CompletableFuture<CoapResponse> apply(CoapRequest request, Service<CoapRequest, CoapResponse> service) {
        CompletableFuture<CoapResponse> resp = service.apply(request);

        resp.thenAccept(r -> {
            if (r.next != null) {
                sendNext(r.next, new ResponseBuilder(request.getToken(), request.getPeerAddress(), request.getTransContext()));
            }
        });

        return resp;
    }

    private void sendNext(Supplier<CompletableFuture<CoapResponse>> next, ResponseBuilder packetBuilder) {
        CompletableFuture<CoapResponse> nextResp = next.get();
        if (nextResp == null) {
            return;
        }

        nextResp.thenCompose(
                obs -> sendNotification
                        .apply(packetBuilder.from(obs), packetBuilder.transContext)
                        .thenApply(resp -> isSuccessfulDelivery(obs, resp))
        ).thenAccept(
                ok -> {
                    if (ok) {
                        sendNext(next, packetBuilder);
                    }
                }
        ).exceptionally(ex -> {
            if (!(ex.getCause() instanceof CancellationException)) {
                LOGGER.warn("Failed to deliver observation: {}", ex.toString());
            }
            return null;
        });
    }

    private boolean isSuccessfulDelivery(CoapResponse obs, CoapPacket resp) {
        return obs.getCode().getHttpCode() < 299 && resp.getMessageType() == MessageType.Acknowledgement;
    }

    private static class ResponseBuilder {
        private final Opaque token;
        private final InetSocketAddress peerAddress;
        private final TransportContext transContext;

        ResponseBuilder(Opaque token, InetSocketAddress peerAddress, TransportContext transContext) {
            this.token = token;
            this.peerAddress = peerAddress;
            this.transContext = transContext;
        }

        public CoapPacket from(CoapResponse resp) {
            CoapPacket coapNotif = new CoapPacket(peerAddress);
            coapNotif.setCode(resp.getCode());
            coapNotif.setToken(token);
            coapNotif.setHeaderOptions(resp.options().duplicate());
            coapNotif.setPayload(resp.getPayload());

            return coapNotif;
        }
    }
}
