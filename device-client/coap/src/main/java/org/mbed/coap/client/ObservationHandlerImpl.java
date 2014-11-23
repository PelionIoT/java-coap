/*
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.client;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mbed.coap.BlockOption;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.Code;
import org.mbed.coap.exception.CoapCodeException;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.exception.ObservationTerminatedException;
import org.mbed.coap.server.CoapExchange;
import org.mbed.coap.server.ObservationHandler;
import org.mbed.coap.utils.Callback;

/**
 * @author szymon
 */
class ObservationHandlerImpl implements ObservationHandler {

    private static final Logger LOGGER = Logger.getLogger(ObservationHandlerImpl.class.getName());
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
                    LOGGER.log(Level.SEVERE, coapException.getMessage(), coapException);
                }
                return;
            }

        }
        LOGGER.warning(ex.getMessage());
    }

    @Override
    public void call(CoapExchange t) {
        final ObservationListenerContainer obsListContainer = observationMap.get(new Token(t.getRequest().getToken()));
        if (obsListContainer != null) {
            try {
                BlockOption requestBlock2Res = t.getRequest().headers().getBlock2Res();
                if (requestBlock2Res != null && requestBlock2Res.getNr() == 0) {
                    t.sendResponse();
                    t.retrieveNotificationBlocks(obsListContainer.uriPath, new Callback<CoapPacket>() {
                        @Override
                        public void callException(Exception ex) {
                            LOGGER.warning(ex.getMessage());
                        }

                        @Override
                        public void call(CoapPacket coapPacket) {
                            try {
                                obsListContainer.observationListener.onObservation(coapPacket);
                            } catch (CoapException e) {
                                LOGGER.log(Level.SEVERE, e.getMessage(), e);
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
            LOGGER.warning("Could not handle observation");
            t.sendResetResponse();
        }
    }

    void putObservationListener(ObservationListener observationListener, byte[] token, String uriPath) {
        observationMap.put(new Token(token), new ObservationListenerContainer(uriPath, observationListener));
    }

    @Override
    public boolean hasObservation(byte[] token) {
        return observationMap.containsKey(new Token(token));
    }

    private static class Token {

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
