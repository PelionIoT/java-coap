package org.mbed.coap.test;

import org.mbed.coap.transport.AbstractTransportContext;
import org.mbed.coap.transport.TransportContext;

/**
 *
 * @author szymon
 */
public class SingleParamTransportContext<E extends Enum<?>> extends AbstractTransportContext<E> {

    private final Object param;
    private final Enum<?> type;

    public SingleParamTransportContext(Enum<?> type, Object param, TransportContext wrapped) {
        super(wrapped);
        this.param = param;
        this.type = type;
    }

    @Override
    protected Object getParameter(E enumerator) {
        if (type.equals(enumerator)) {
            return param;
        }
        return null;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 71 * hash + (this.param != null ? this.param.hashCode() : 0);
        hash = 71 * hash + (this.type != null ? this.type.hashCode() : 0);
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
        final SingleParamTransportContext<?> other = (SingleParamTransportContext<?>) obj;
        if (this.param != other.param && (this.param == null || !this.param.equals(other.param))) {
            return false;
        }
        if (this.type != other.type) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "Context [" + param + "]";
    }

}
