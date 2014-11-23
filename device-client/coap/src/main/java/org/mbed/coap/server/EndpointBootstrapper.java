/*
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mbed.coap.CoapConstants;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.Code;
import org.mbed.coap.MediaTypes;
import org.mbed.coap.Method;
import org.mbed.coap.exception.CoapCodeException;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.utils.Callback;
import org.mbed.coap.utils.CoapResource;

/**
 * @author nordav01
 */
public class EndpointBootstrapper {

    private static final Logger LOG = Logger.getLogger(EndpointBootstrapper.class.getName());
    private static final String BSPATH = "/bs";

    private BootstrappingState state = BootstrappingState.NOT_BOOTSTRAPPED;
    private final CoapServer server;
    private final InetSocketAddress bsAddress;
    private String bsPath = BSPATH;
    private final String endpointName;
    private String type;
    private String domain;
    private InetSocketAddress dsAddress;
    private CoapHandler bootstrapResponseHandler;

    public static enum BootstrappingState {

        NOT_BOOTSTRAPPED, BOOTSTRAP_REQUESTED, BOOTSTRAPPED, BOOTSTRAP_FAILED
    }

    public EndpointBootstrapper(CoapServer server, InetSocketAddress bsAddress, String endpointName) {
        this.server = server;
        this.bsAddress = bsAddress;
        this.endpointName = endpointName;
    }

    public String getEndpointName() {
        return endpointName;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getDomain() {
        return domain;
    }

    public BootstrappingState getState() {
        return state;
    }

    public InetSocketAddress getDsAddress() {
        return dsAddress;
    }

    public void setBsPath(String bsPath) {
        this.bsPath = bsPath;
    }

    private String getNodeName() {
        return endpointName + "@" + domain;
    }

    public void bootstrap() {
        bootstrap(null);
    }

    public void bootstrap(Callback<BootstrappingState> callback) {
        if (state == BootstrappingState.NOT_BOOTSTRAPPED) {
            makeBootstrap(callback);
        }
    }

    private void makeBootstrap(final Callback<BootstrappingState> callback) {
        bootstrapResponseHandler = createBootstrapHandler(callback);
        server.addRequestHandler("/0", bootstrapResponseHandler);
        server.addRequestHandler("/0/*", bootstrapResponseHandler);

        CoapPacket coap = new CoapPacket(bsAddress);
        coap.setMethod(Method.POST);
        coap.headers().setAccept(new short[]{MediaTypes.CT_APPLICATION_LWM2M_TEXT});
        coap.headers().setUriPath(bsPath);
        coap.headers().setUriQuery(getUriQuery());
        coap.headers().setUriHost(domain);
        coap.setMessageId(server.getNextMID());

        try {
            sendBootstrap(coap, callback);
        } catch (CoapException exception) {
            bootstrapResponse(exception, callback);
        }

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Bootstrap request sent to: " + bsAddress.getHostString() + ":" + bsAddress.getPort() + bsPath + "?" + coap.headers().getUriQuery());
        }
    }

    private void sendBootstrap(CoapPacket coap, final Callback<BootstrappingState> callback) throws CoapException {
        server.makeRequest(coap, new Callback<CoapPacket>() {
            @Override
            public void call(CoapPacket coap) {
                bootstrapResponse(coap, callback);
            }

            @Override
            public void callException(Exception exception) {
                bootstrapResponse(exception, callback);
            }
        });
    }

    private void bootstrapResponse(CoapPacket coap, Callback<BootstrappingState> callback) {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine(getNodeName() + " response for bootstrap request: " + coap);
        }

        Code code = coap.getCode();
        if (code == Code.C204_CHANGED || code == Code.C203_VALID || code == Code.C201_CREATED) {
            state = BootstrappingState.BOOTSTRAP_REQUESTED;
        } else {
            state = BootstrappingState.BOOTSTRAP_FAILED;
            removeBootstrapResponseHandler();
        }

        if (callback != null) {
            callback.call(state);
        }
    }

    private void bootstrapResponse(Exception exception, Callback<BootstrappingState> callback) {
        LOG.fine("Bootstrap error: " + exception);
        state = BootstrappingState.BOOTSTRAP_FAILED;
        removeBootstrapResponseHandler();

        if (callback != null) {
            callback.callException(exception);
        }
    }

    private CoapHandler createBootstrapHandler(final Callback<BootstrappingState> callback) {
        return new CoapResource() {

            @Override
            public void get(CoapExchange exchange) throws CoapCodeException {
                throw new CoapCodeException(Code.C405_METHOD_NOT_ALLOWED);
            }

            @Override
            public void put(CoapExchange exchange) throws CoapCodeException {
                if (exchange.getRequestHeaders().getContentFormat() != MediaTypes.CT_APPLICATION_LWM2M_TEXT) {
                    exchange.setResponseCode(Code.C406_NOT_ACCEPTABLE);
                    exchange.sendResponse();
                    return;
                }

                String uri = exchange.getRequestUri();
                String body = exchange.getRequestBodyString();
                exchange.setResponseCode(Code.C204_CHANGED);
                exchange.sendResponse();
                if (uri.matches("/0/[\\d]+/0")) {
                    handleServerAddress(body, callback);
                } else if (uri.matches("/0/[\\d]+/366")) {
                    EndpointBootstrapper.this.domain = body;
                }
            }

            @Override
            public void delete(CoapExchange exchange) throws CoapCodeException {
                exchange.setResponseCode(Code.C202_DELETED);
                exchange.sendResponse();
            }

        };
    }

    private void handleServerAddress(String address, Callback<BootstrappingState> callback) {
        URI uri = URI.create(address);
        if (uri.getPort() == -1) {
            dsAddress = new InetSocketAddress(uri.getHost(), CoapConstants.DEFAULT_PORT);
        } else {
            dsAddress = new InetSocketAddress(uri.getHost(), uri.getPort());
        }

        removeBootstrapResponseHandler();
        state = BootstrappingState.BOOTSTRAPPED;
        if (callback != null) {
            callback.call(state);
        }
    }

    private void removeBootstrapResponseHandler() {
        if (bootstrapResponseHandler != null) {
            server.removeRequestHandler(bootstrapResponseHandler);
            bootstrapResponseHandler = null;
        }
    }

    private String getUriQuery() {
        StringBuilder uriQuery = new StringBuilder();
        if (endpointName != null) {
            uriQuery.append("&ep=").append(endpointName);
        }
        if (type != null && type.length() > 0) {
            uriQuery.append("&et=").append(type);
        }
        if (domain != null && !domain.isEmpty()) {
            uriQuery.append("&d=").append(domain);
        }

        if (uriQuery.length() == 0) {
            return null;
        }
        return uriQuery.substring(1);
    }

    @Override
    public String toString() {
        return bsAddress.getAddress() + ":" + bsAddress.getPort() + bsPath + "?" + getUriQuery();
    }

}
