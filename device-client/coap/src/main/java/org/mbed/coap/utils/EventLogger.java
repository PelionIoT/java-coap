/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.utils;

import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author nordav01
 */
public class EventLogger {

    private static final String EVENT_LOGGER = "com.sensinode.nanoservice.eventlog";
    public static final String COAP_RECEIVED = "CoAP received";
    public static final String COAP_SENT = "CoAP sent";
    private static final EventLogger NULL = new NullEventLogger();

    private Object formatter;
    private java.lang.reflect.Method format;
    private Logger logger;
    private final static Class<?> EVENT_LOG_CLASS;

    static {
        Class<?> eventLogClass = null;
        try {
            eventLogClass = EventLogger.class.getClassLoader().loadClass(EVENT_LOGGER + ".NanoEventFormatter"); //NOPMD
        } catch (ClassNotFoundException ex) {
            //expected, ignore
        }
        EVENT_LOG_CLASS = eventLogClass;
    }

    public static EventLogger getLogger(String channel) {
        if (EVENT_LOG_CLASS != null) {
            try {
                return new EventLogger(LoggerFactory.getLogger(EVENT_LOGGER + ".NanoEvent." + channel), EVENT_LOG_CLASS.newInstance());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        return NULL;
    }

    private EventLogger() {
    }

    private EventLogger(Logger logger, Object renderer) throws NoSuchMethodException, SecurityException {
        this.logger = logger;
        this.formatter = renderer;
        this.format = renderer.getClass().getMethod("format", String.class, InetSocketAddress.class, Object.class);
    }

    public void fatal(String category, InetSocketAddress address, Object details) {
        if (logger.isErrorEnabled() ) {
            logger.error(render(category, address, details));
        }
    }

    public void error(String category, InetSocketAddress address, Object details) {
        if (logger.isErrorEnabled() ) {
            logger.error(render(category, address, details));
        }
    }

    public void warn(String category, InetSocketAddress address, Object details) {
        if (logger.isWarnEnabled() ) {
            logger.warn(render(category, address, details));
        }
    }

    public void info(String category, InetSocketAddress address, Object details) {
        if (logger.isInfoEnabled() ) {
            logger.info(render(category, address, details));
        }
    }

    private String render(String category, InetSocketAddress address, Object details) {
        try {
            return format.invoke(formatter, category, address, details).toString();
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException(ex);
        } catch (InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
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
