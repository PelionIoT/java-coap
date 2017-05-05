/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package com.mbed.lwm2m.transport;

/**
 * Implements transport binding as specified in chapter 5.2.1.1
 *
 * @author szymon
 */
public final class TransportBinding {

    private final boolean isUDP;
    private final boolean isSMS;
    private final boolean isQueueMode;

    public static final TransportBinding DEFAULT = new TransportBinding(true, false, false);

    TransportBinding(boolean isUDP, boolean isSMS, boolean isQueueMode) {
        this.isUDP = isUDP;
        this.isSMS = isSMS;
        this.isQueueMode = isQueueMode;
    }

    public static TransportBinding parse(String binding) throws TransportBindingParseException {
        switch (binding) {
            case "U":
                return new TransportBinding(true, false, false);
            case "UQ":
                return new TransportBinding(true, false, true);
            case "S":
                return new TransportBinding(false, true, false);
            case "SQ":
                return new TransportBinding(false, true, true);
            case "US":
                return new TransportBinding(true, true, false);
            case "UQS":
                return new TransportBinding(true, true, true);
            default:
                throw new TransportBindingParseException();
        }
    }

    public boolean isQueueMode() {
        return isQueueMode;
    }

    public boolean isSMS() {
        return isSMS;
    }

    public boolean isUDP() {
        return isUDP;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + (this.isUDP ? 1 : 0);
        hash = 97 * hash + (this.isSMS ? 1 : 0);
        hash = 97 * hash + (this.isQueueMode ? 1 : 0);
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
        final TransportBinding other = (TransportBinding) obj;
        if (this.isUDP != other.isUDP) {
            return false;
        }
        if (this.isSMS != other.isSMS) {
            return false;
        }
        if (this.isQueueMode != other.isQueueMode) {
            return false;
        }
        return true;
    }
    
    @Override
    public String toString() {
        if (isUDP && isSMS && isQueueMode) {
            return "UQS";
        }
            
        StringBuilder builder = new StringBuilder(3);
        if (isUDP) {
            builder.append('U');
        }
        if (isSMS) {
            builder.append('S');
        }
        if (isQueueMode) {
            builder.append('Q');
        }
        return builder.toString();
    }

}
