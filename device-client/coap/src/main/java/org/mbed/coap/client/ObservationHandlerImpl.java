/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.client;

import org.mbed.coap.Code;
import org.mbed.coap.exception.CoapCodeException;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.exception.ObservationTerminatedException;
import org.mbed.coap.server.CoapExchange;
import org.mbed.coap.server.ObservationHandler;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author szymon
 */
class ObservationHandlerImpl implements ObservationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ObservationHandlerImpl.class);
    private final Map<Token, ObservationListener> observationMap = new HashMap<Token, ObservationListener>();

    @Override
    public void callException(Exception ex) {
        if (ex instanceof ObservationTerminatedException) {
            ObservationTerminatedException termEx = (ObservationTerminatedException) ex;
            ObservationListener obsListener = observationMap.get(new Token(termEx.getNotification().getToken()));
            if (obsListener != null) {
                try {
                    obsListener.onTermination(termEx.getNotification());
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
        ObservationListener obsListener = observationMap.get(new Token(t.getRequest().getToken()));
        if (obsListener != null) {
            try {
                obsListener.onObservation(t.getRequest());
                t.sendResponse();
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

    void putObservationListener(ObservationListener observationListener, byte[] token) {
        observationMap.put(new Token(token), observationListener);
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

}
