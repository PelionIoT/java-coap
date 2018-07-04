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
package com.mbed.coap.client;

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Method;
import java.util.concurrent.ExecutionException;

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

    public CoapPacket invokeMethod(Method method) throws CoapException {
        switch (method) {
            case GET:
                return get();
            case DELETE:
                return delete();
            case PUT:
                return put();
            case POST:
                return post();
            default:
                throw new RuntimeException("Method not supported");
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
        } else {
            return new CoapException(ex);
        }
    }

}
