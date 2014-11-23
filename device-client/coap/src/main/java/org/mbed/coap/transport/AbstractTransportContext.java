/*
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.transport;


import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract class for transport context, that optionally wraps another instance
 * of {@link org.mbed.coap.transport.TransportContext}.
 *
 * @author szymon
 */
public abstract class AbstractTransportContext<E extends Enum<?>> implements TransportContext {

    private static final Logger LOGGER = Logger.getLogger(AbstractTransportContext.class.getName());
    private final TransportContext wrappedTransContext;

    protected AbstractTransportContext(TransportContext wrappedTransContext) {
        this.wrappedTransContext = wrappedTransContext;
    }

    @SuppressWarnings("unchecked")
    @Override
    public final <T> T get(Enum<?> enumerator, Class<T> clazz) {
        Object retValue;
        try {
            retValue = getParameter((E) enumerator);
            try {
                return clazz.cast(retValue);
            } catch (ClassCastException ex) {
                LOGGER.log(Level.WARNING, "Could not cast transport context parameter: {}", ex.getMessage());
            }
        } catch (ClassCastException ex) {
            LOGGER.finest("Could not cast enumerator: " + ex.getMessage());
        }

        if (wrappedTransContext != null) {
            return wrappedTransContext.get(enumerator, clazz);
        }
        return null;
    }

    protected abstract Object getParameter(E enumerator);

    @Override
    public final Object get(Enum<?> enumerator) {
        return get(enumerator, Object.class);
    }

}
