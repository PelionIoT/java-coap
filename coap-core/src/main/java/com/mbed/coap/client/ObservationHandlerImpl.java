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
package com.mbed.coap.client;

import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.exception.ObservationTerminatedException;
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.server.CoapExchange;
import com.mbed.coap.server.ObservationHandler;
import com.mbed.coap.utils.Callback;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author szymon
 */
class ObservationHandlerImpl implements ObservationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ObservationHandlerImpl.class.getName());
    private final Map<Token, ObservationListenerContainer> observationMap = new HashMap<>();

    @Override
    public void callException(Exception ex) {
        if (ex instanceof ObservationTerminatedException) {
            ObservationTerminatedException termEx = (ObservationTerminatedException) ex;
            ObservationListenerContainer obsListContainer = observationMap.get(new Token(termEx.getNotification().getToken()));
            if (obsListContainer != null) {
                try {
                    obsListContainer.observationListener.onTermination(termEx.getNotification());
                } catch (CoapException coapException) {
                    LOGGER.error(coapException.getMessage(), coapException);
                }
                return;
            }

        }
        LOGGER.warn(ex.getMessage());
    }

    @Override
    public void call(CoapExchange t) {
        final ObservationListenerContainer obsListContainer = observationMap.get(new Token(t.getRequest().getToken()));
        if (obsListContainer != null) {
            try {
                // TODO: BERT support + should be moved to CoapServerBlocks
                BlockOption requestBlock2Res = t.getRequest().headers().getBlock2Res();
                if (requestBlock2Res != null && requestBlock2Res.getNr() == 0 && requestBlock2Res.hasMore()) {
                    if (requestBlock2Res.hasMore() && requestBlock2Res.getSize() != t.getRequestBody().length) {
                        LOGGER.warn("Block size does not match payload size " + requestBlock2Res.getSize() + "!=" + t.getRequestBody().length);
                        t.setResponse(resetResponse(t));
                        t.sendResponse();
                        return;
                    }
                    t.sendResponse();
                    t.retrieveNotificationBlocks(obsListContainer.uriPath, new Callback<CoapPacket>() {
                        @Override
                        public void callException(Exception ex) {
                            LOGGER.warn(ex.getMessage());
                        }

                        @Override
                        public void call(CoapPacket coapPacket) {
                            try {
                                obsListContainer.observationListener.onObservation(coapPacket);
                            } catch (CoapException e) {
                                LOGGER.error(e.getMessage(), e);
                            }
                        }
                    });
                } else {
                    obsListContainer.observationListener.onObservation(t.getRequest());
                    t.sendResponse();
                }
            } catch (ObservationTerminatedException ex) {
                t.sendResetResponse();
            } catch (CoapCodeException ex) {
                t.setResponseCode(ex.getCode());
                t.sendResponse();
            } catch (CoapException ex) {
                t.setResponseCode(Code.C500_INTERNAL_SERVER_ERROR);
                t.sendResponse();
            }
        } else {
            LOGGER.warn("Could not handle observation");
            t.sendResetResponse();
        }
    }

    private static CoapPacket resetResponse(CoapExchange t) {
        CoapPacket resetResponse = new CoapPacket(t.getRemoteAddress());
        resetResponse.setMessageType(MessageType.Reset);
        resetResponse.setMessageId(t.getRequest().getMessageId());
        return resetResponse;
    }

    void putObservationListener(ObservationListener observationListener, byte[] token, String uriPath) {
        observationMap.put(new Token(token), new ObservationListenerContainer(uriPath, observationListener));
    }

    @Override
    public boolean hasObservation(byte[] token) {
        return observationMap.containsKey(new Token(token));
    }

    static class Token {

        private final byte[] tokenVal;

        public Token(byte[] tokenVal) {
            this.tokenVal = tokenVal;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 37 * hash + Arrays.hashCode(this.tokenVal);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Token other = (Token) obj;
            if (!Arrays.equals(this.tokenVal, other.tokenVal)) {
                return false;
            }
            return true;
        }

    }

    private static class ObservationListenerContainer {
        private final String uriPath;
        private final ObservationListener observationListener;

        ObservationListenerContainer(String uriPath, ObservationListener observationListener) {
            this.uriPath = uriPath;
            this.observationListener = observationListener;
        }
    }
}
