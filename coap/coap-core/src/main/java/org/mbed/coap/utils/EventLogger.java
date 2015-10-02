/*
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.utils;

import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author nordav01
 */
public class EventLogger {

    private static final String NANO_EVENT = "com.arm.mbed.commons.eventlog.NanoEvent";
    public static final String COAP_RECEIVED = "CoAP received";
    public static final String COAP_SENT = "CoAP sent";
    private static final EventLogger NULL = new NullEventLogger();

    private EventLoggerFormatter formatter;
    private Logger logger;
    private final static Class<?> EVENT_CLASS;

    static {
        Class<?> eventClass = null;
        try {
            eventClass = EventLogger.class.getClassLoader().loadClass(NANO_EVENT); //NOPMD
        } catch (ClassNotFoundException ex) {
            //expected, ignore
        }
        EVENT_CLASS = eventClass;
    }

    public static EventLogger getLogger(String channel) {
        if (EVENT_CLASS != null) {
            try {
                return new EventLogger(Logger.getLogger(NANO_EVENT + "." + channel), new EventLoggerFormatter());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        return NULL;
    }

    private EventLogger() {
    }

    private EventLogger(Logger logger, EventLoggerFormatter renderer) throws NoSuchMethodException, SecurityException {
        this.logger = logger;
        this.formatter = renderer;
    }

    public void fatal(String category, InetSocketAddress address, Object details) {
        if (logger.isLoggable(Level.SEVERE)) {
            logger.severe(render(category, address, details));
        }
    }

    public void error(String category, InetSocketAddress address, Object details) {
        if (logger.isLoggable(Level.SEVERE)) {
            logger.severe(render(category, address, details));
        }
    }

    public void warn(String category, InetSocketAddress address, Object details) {
        if (logger.isLoggable(Level.WARNING)) {
            logger.warning(render(category, address, details));
        }
    }

    public void info(String category, InetSocketAddress address, Object details) {
        if (logger.isLoggable(Level.INFO)) {
            logger.info(render(category, address, details));
        }
    }

    private String render(String category, InetSocketAddress address, Object details) {
        return formatter.format(category, address, details);
    }

    private static class NullEventLogger extends EventLogger {

        @Override
        public void fatal(String category, InetSocketAddress address, Object details) {
            // NOP
        }

        @Override
        public void warn(String category, InetSocketAddress address, Object details) {
            // NOP
        }

        @Override
        public void error(String category, InetSocketAddress address, Object details) {
            // NOP
        }

        @Override
        public void info(String category, InetSocketAddress address, Object details) {
            // NOP
        }
    }

}
