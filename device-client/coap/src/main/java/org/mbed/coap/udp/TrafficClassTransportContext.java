package org.mbed.coap.udp;

import org.mbed.coap.transport.AbstractTransportContext;
import org.mbed.coap.transport.TransportContext;

/**
 *
 * @author szymon
 */
public class TrafficClassTransportContext extends AbstractTransportContext<TrafficClassTransportContext.Type> {

    private final Integer trafficClass;
    public static final int DEFAULT = 0;
    public static final int HIGH = 127;
    public static final int HIGHEST = 255;

    public static TrafficClassTransportContext height() {
        return new TrafficClassTransportContext(HIGH, NULL);
    }

    public static TrafficClassTransportContext highest() {
        return new TrafficClassTransportContext(HIGHEST, NULL);
    }

    public TrafficClassTransportContext(int trafficClass, TransportContext wrapped) {
        super(wrapped);
        if (trafficClass < 0 || trafficClass > 255) {
            throw new IllegalArgumentException("Traffic class out of range");
        }
        this.trafficClass = trafficClass;
    }

    @Override
    protected Object getParameter(Type enumerator) {
        switch (enumerator) {
            case TRAFFIC_CLASS:
                return trafficClass;
            default:
                return null;
        }
    }

    public static enum Type {

        TRAFFIC_CLASS
    }

}
