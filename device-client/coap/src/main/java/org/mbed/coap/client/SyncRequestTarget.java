/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.client;

import java.util.concurrent.ExecutionException;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.exception.CoapException;

/**
 * @author szymon
 */
public class SyncRequestTarget {

    private final CoapRequestTarget coapRequestTarget;

    SyncRequestTarget(final CoapRequestTarget coapRequestTarget) {
        this.coapRequestTarget = coapRequestTarget;
    }

    public CoapPacket put() throws CoapException {
        try {
            return coapRequestTarget.put().get();
        } catch (Exception ex) {
            throw handleException(ex);
        }
    }

    public CoapPacket get() throws CoapException {
        try {
            return coapRequestTarget.get().get();
        } catch (Exception ex) {
            throw handleException(ex);
        }
    }

    public CoapPacket delete() throws CoapException {
        try {
            return coapRequestTarget.delete().get();
        } catch (Exception ex) {
            throw handleException(ex);
        }
    }

    public CoapPacket post() throws CoapException {
        try {
            return coapRequestTarget.post().get();
        } catch (Exception ex) {
            throw handleException(ex);
        }
    }

    public CoapPacket observe(ObservationListener obsListener) throws CoapException {
        try {
            return coapRequestTarget.observe(obsListener).get();
        } catch (Exception ex) {
            throw handleException(ex);
        }
    }

    private static CoapException handleException(Exception ex) {
        if (ex instanceof ExecutionException) {
            return handleException(((Exception) ex.getCause()));
        }
        if (ex instanceof CoapException) {
            return (CoapException) ex;
        } else if (ex.getCause() instanceof CoapException) {
            return (CoapException) ex.getCause();
        } else {
            return new CoapException(ex);
        }
    }

}
