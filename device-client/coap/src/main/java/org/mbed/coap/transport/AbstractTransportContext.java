package org.mbed.coap.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract class for transport context, that optionally wraps another instance
 * of {@link org.mbed.coap.transport.TransportContext}.
 *
 * @author szymon
 */
public abstract class AbstractTransportContext<E extends Enum<?>> implements TransportContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTransportContext.class);
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
                LOGGER.warn("Could not cast transport context parameter: {}", ex.getMessage());
            }
        } catch (ClassCastException ex) {
            LOGGER.trace("Could not cast enumerator: {}", ex.getMessage());
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
