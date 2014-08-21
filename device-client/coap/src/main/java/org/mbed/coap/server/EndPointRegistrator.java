/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server;

import org.mbed.coap.CoapPacket;
import org.mbed.coap.Code;
import org.mbed.coap.MediaTypes;
import org.mbed.coap.Method;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.linkformat.LinkFormat;
import org.mbed.coap.linkformat.LinkFormatBuilder;
import org.mbed.coap.utils.Callback;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author szymon
 */
public class EndPointRegistrator {

    private static final Logger LOG = LoggerFactory.getLogger(EndPointRegistrator.class);
    private static final String RDPATH = "/rd";
    private String rdPath = RDPATH;
    private final CoapServer server;
    private final InetSocketAddress rdAddress;
    private int lifeTimeSec = 3600; //1h
    private long registrationTimeOut = Long.MAX_VALUE;
    private RegistrationState state = RegistrationState.NOT_REGISTERED;
    private String registrLocation;
    private String domain;
    private String hostName;
    private String instance;
    private String type;
    private String context;
    private ScheduledFuture<?> nextRegistrationSchedule;
    private List<LinkFormat> registeredLinks;
    private int configuredRegisterTimeMaxDelay;
    private int configuredRegisterTimeMinDelay;
    private int configuredRegisterRetries;
    private int currentRetryCount;
    private boolean enableTemplate;

    private void logState() {
        if (LOG.isTraceEnabled()) {
            LOG.trace(hostName + " " + state.toString());
        }
    }

    public String getDomain() {
        return domain;
    }

    public enum RegistrationState {

        NOT_REGISTERED, REGISTRATION_SENT, REGISTRATION_TIMEOUT, REGISTERED,
        FAILED
    }

    public EndPointRegistrator(CoapServer server, InetSocketAddress rdAddress, String hostName) {
        this.server = server;
        this.rdAddress = rdAddress;
        this.hostName = hostName;
    }

    public void setLifeTime(int lifeTimeSec) {
        this.lifeTimeSec = lifeTimeSec;
    }

    /**
     * Sets domain attribute
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    /**
     * Sets host name attribute
     */
    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    /**
     * Sets context attribute
     */
    public void setContext(String context) {
        this.context = context;
    }

    /**
     * Sets instance attribute
     */
    public void setInstance(String instance) {
        this.instance = instance;
    }

    /**
     * Sets resource type attribute
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Test use only
     */
    public int getCurrentRetryCount() {
        return currentRetryCount;
    }

    /**
     * Test use only
     */
    public void setCurrentRetryCount(int currentRetryCount) {
        this.currentRetryCount = currentRetryCount;
    }

    /**
     * Returns registration state.
     */
    public RegistrationState getState() {
        return state;
    }

    public void setRegisterLimits(int min, int max, int retries) {
        this.configuredRegisterTimeMinDelay = min;
        this.configuredRegisterTimeMaxDelay = max;
        this.configuredRegisterRetries = retries;
    }

    public void setEnableTemplate(boolean enableTemplate) {
        this.enableTemplate = enableTemplate;
    }

    /**
     * Sends registration to NanoServicePlatform
     */
    public void register() {
        register(null);
    }

    public void register(Callback<RegistrationState> callback) {
        if (state == RegistrationState.REGISTERED) {
            makeRegistrationUpdate(callback);
        } else {
            makeRegistration(callback);
        }

    }

    /**
     * Sets resource directory uri path that registration will be made on
     */
    public void setRdPath(String rdPath) {
        this.rdPath = rdPath;
    }

    private String getNodeName() {
        return hostName + "@" + domain;
    }

    @SuppressWarnings({"PMD.CyclomaticComplexity", "PMD.NPathComplexity"}) //produces uri-query
    private String getUriQuery() {

        StringBuilder uriQuery = new StringBuilder();
        if (hostName != null) {
            uriQuery.append("&ep=").append(hostName);
        }
        if (lifeTimeSec > 0) {
            uriQuery.append("&lt=").append(lifeTimeSec);
        }
        if (instance != null && instance.length() > 0) {
            uriQuery.append("&ins=").append(instance);
        }
        if (type != null && type.length() > 0) {
            uriQuery.append("&et=").append(type);
        }
        if (context != null && context.length() > 0) {
            uriQuery.append("&con=").append(context);
        }

        if (domain != null && !domain.isEmpty()) {
            uriQuery.append("&d=").append(domain);
        }

        if (uriQuery.length() == 0) {
            return null;
        }
        return uriQuery.substring(1);
    }

    private void makeRegistrationUpdate(final Callback<RegistrationState> callback) {
        if (registrLocation == null || registrLocation.isEmpty() || isTimeout()) {
            makeRegistration(callback);
            return;
        }
        final List<LinkFormat> links = server.getResourceLinks();
        CoapPacket coap = new CoapPacket();
        coap.setMethod(Method.PUT);
        coap.headers().setUriPath(registrLocation);
        coap.headers().setUriQuery(getUriQuery());
        //coap.headers().setUriHost(domain);
        coap.headers().setContentFormat(MediaTypes.CT_APPLICATION_LINK__FORMAT);
        coap.setMessageId(server.getNextMID());
        coap.setAddress(rdAddress);

        List<LinkFormat> addedLinks = findAdditionalLinks(links);
        if (addedLinks != null && !addedLinks.isEmpty()) {
            coap.setPayload(LinkFormatBuilder.toString(addedLinks));
        }

        state = RegistrationState.REGISTRATION_SENT;
        if (LOG.isDebugEnabled()) {
            LOG.debug("Registration Update sent to: " + rdAddress.getHostName() + ":" + rdAddress.getPort() + registrLocation + (coap.headers().getUriQuery() != null ? "?" + coap.headers().getUriQuery() : ""));
            //LOG.debug("Registration sent to: " + rdAddress.getHostName() + ":" + rdAddress.getPort() + rdPath + "?" + getUriQuery());            
        }

        RegistratorRunnable regRunnable = new RegistratorRunnable(coap, callback, links);
        server.getScheduledExecutor().schedule(regRunnable, 0, TimeUnit.SECONDS);
    }

    private List<LinkFormat> findAdditionalLinks(final List<LinkFormat> links) {
        //FIND ADDITIONAL LINKS
        List<LinkFormat> addedLinks = null;
        if (!registeredLinks.equals(links)) {
            addedLinks = new ArrayList<LinkFormat>();
            for (LinkFormat lf : links) {
                if (!registeredLinks.contains(lf)) {
                    addedLinks.add(lf);
                }
            }
        }
        return addedLinks;
    }

    private void makeRegistration(final Callback<RegistrationState> callback) {
        final List<LinkFormat> links = server.getResourceLinks();

        String resources = "";
        if (!enableTemplate) {
            resources = LinkFormatBuilder.toString(links);
        }

        CoapPacket coap = new CoapPacket();
        coap.setMethod(Method.POST);
        coap.headers().setUriPath(rdPath);
        coap.headers().setUriQuery(getUriQuery());
        coap.headers().setUriHost(domain);
        coap.headers().setContentFormat(MediaTypes.CT_APPLICATION_LINK__FORMAT);
        coap.setMessageId(server.getNextMID());
        coap.setPayload(resources);
        coap.setAddress(rdAddress);

        state = RegistrationState.REGISTRATION_SENT;
        if (LOG.isDebugEnabled()) {
            LOG.debug("Registration sent to: " + rdAddress.getHostName() + ":" + rdAddress.getPort() + rdPath + (coap.headers().getUriQuery() != null ? "?" + coap.headers().getUriQuery() : ""));
            //LOG.debug("Registration sent to: " + rdAddress.getHostName() + ":" + rdAddress.getPort() + rdPath + "?" + getUriQuery());            
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("Registration payload:" + resources);
        }

        RegistratorRunnable regRunnable = new RegistratorRunnable(coap, callback, links);
        int randomDelay = getRegisterRandomDithering();
        server.getScheduledExecutor().schedule(regRunnable, randomDelay, TimeUnit.SECONDS);

    }

    private void registrationResponse(CoapPacket coap, List<LinkFormat> links, final Callback<RegistrationState> callback) {
        if (LOG.isTraceEnabled()) {
            LOG.trace(EndPointRegistrator.this.getNodeName() + " registration response: " + coap);
        }
        if (coap.getCode() == Code.C201_CREATED || coap.getCode() == Code.C204_CHANGED) {
            checkRegistrationLocation(coap);
            state = RegistrationState.REGISTERED;
            currentRetryCount = 0;
            registrationTimeOut = System.currentTimeMillis() + lifeTimeSec * 1000l;

            // randomize re-registrer time based on min and max difference
            long delay = getReregisterDelay();
            nextRegistrationSchedule = server.getScheduledExecutor().schedule(new Runnable() {
                @Override
                public void run() {
                    register(callback);
                }
            }, delay, TimeUnit.MILLISECONDS);

            registeredLinks = links;
            logState();
            if (callback != null) {
                callback.call(state);
            }
        } else {
            LOG.warn("Could not register (" + coap + ")");
            currentRetryCount++;
            state = RegistrationState.FAILED;
            logState();
            if (currentRetryCount <= configuredRegisterRetries) {
                register(callback);
            } else if (callback != null) {
                callback.call(state);
            }
        }
    }

    private void checkRegistrationLocation(CoapPacket coap) {
        if (coap.getCode() == Code.C201_CREATED) {
            registrLocation = coap.headers().getLocationPath();
        }
        if (registrLocation == null) {
            LOG.warn("Registration error, no location returned");
            //LOG.warn(EndPointRegistrator.this.getNodeName() + " registration error, no location returned");
        } else {
            if (coap.getCode() == Code.C201_CREATED) {
                LOG.info("Registration created on: " + registrLocation);
            } else {
                LOG.info("Registration updated on: " + registrLocation);
            }
            //LOG.debug(EndPointRegistrator.this.getNodeName() + " registration created on: " + registrLocation);
        }
    }

    /**
     * Calculates re-register delay based on limits, value lifeTimeSec deducted
     * with 30s and random 0 - (max-min), but not under 20,000 ms
     *
     * @return calculated delay in milliseconds
     */
    public long getReregisterDelay() {
        int diff = Math.abs(configuredRegisterTimeMaxDelay - configuredRegisterTimeMinDelay);
        int tempLifeTimeSecs = lifeTimeSec - 30;
        if (diff > 0) {
            tempLifeTimeSecs -= (int) (Math.random() * (diff + 1));
        }
        long delay = tempLifeTimeSecs * 1000l;
        return Math.max(delay, 20000);
    }

    /**
     * Calculates register dithering for registering, value is between min and
     * max delays added with retry count^2 * max delay
     *
     * @return dithering delay in seconds
     */
    public int getRegisterRandomDithering() {
        return (currentRetryCount * currentRetryCount * configuredRegisterTimeMaxDelay)
                + configuredRegisterTimeMinDelay
                + (int) (Math.random() * ((Math.abs(configuredRegisterTimeMaxDelay - configuredRegisterTimeMinDelay)) + 1));
    }

    private void registrationResponse(Exception exception, Callback<RegistrationState> callback) {
        LOG.debug("Registration error: " + exception);
        state = RegistrationState.FAILED;
        logState();
        currentRetryCount++;
        if (currentRetryCount <= configuredRegisterRetries) {
            register(callback);
        } else if (callback != null) {
            callback.callException(exception);
        }
    }

    public boolean isTimeout() {
        if (state == RegistrationState.REGISTERED && System.currentTimeMillis() >= registrationTimeOut) {
            return true;
        }
        return false;
    }

    /**
     * Removes registration from NanoServicePlatform
     */
    public void unregister(final Callback<RegistrationState> callback) {
        if (nextRegistrationSchedule != null) {
            nextRegistrationSchedule.cancel(false);
            nextRegistrationSchedule = null;
        }
        if (state != RegistrationState.REGISTERED) {
            callback.call(state);
            return;
        }

        CoapPacket coap = new CoapPacket();
        coap.setMethod(Method.DELETE);
        coap.headers().setUriPath(registrLocation);
        coap.setMessageId(server.getNextMID());
        coap.setAddress(rdAddress);
        try {
            server.makeRequest(coap, new Callback<CoapPacket>() {
                @Override
                public void call(CoapPacket t) {
                    if (t.getCode() != Code.C202_DELETED) {
                        LOG.warn("Could not remove registration (" + t + ")");
                        //LOG.warn(EndPointRegistrator.this.getNodeName() + " Could not remove registration");
                    } else {
                        state = RegistrationState.NOT_REGISTERED;
                        //if (LOG.isDebugEnabled()) {
                        //LOG.debug(EndPointRegistrator.this.getNodeName() + " Registration removed");
                        LOG.info("Registration removed");
                        //}
                    }
                    callback.call(state);
                }

                @Override
                public void callException(Exception ex) {
                    LOG.error(ex.toString(), ex);
                    callback.callException(ex);
                }
            });
        } catch (Exception ex) {
            LOG.error(ex.toString(), ex);
        }

    }

    @Override
    public String toString() {
        return rdAddress.getAddress() + ":" + rdAddress.getPort() + rdPath + (getUriQuery() != null ? "?" + getUriQuery() : "");
    }

    protected void sendRegistration(CoapPacket coap, final List<LinkFormat> links, final Callback<RegistrationState> callback) throws CoapException {
        server.makeRequest(coap, new Callback<CoapPacket>() {
            @Override
            public void call(CoapPacket t) {
                registrationResponse(t, links, callback);
            }

            @Override
            public void callException(Exception ex) {
                registrationResponse(ex, callback);
            }
        });
        if (LOG.isTraceEnabled()) {
            LOG.trace(EndPointRegistrator.this.getNodeName() + " sent registration update request");
        }

    }

    protected class RegistratorRunnable implements Runnable {

        private final Callback<RegistrationState> callback;
        private final CoapPacket coap;
        private final List<LinkFormat> links;

        public RegistratorRunnable(CoapPacket coap, Callback<RegistrationState> callback, List<LinkFormat> links) {
            this.coap = coap;
            this.callback = callback;
            this.links = links;
        }

        @Override
        public void run() {
            try {
                sendRegistration(coap, links, callback);
            } catch (Exception ex) {
                registrationResponse(ex, callback);
            }
        }
    }
}
