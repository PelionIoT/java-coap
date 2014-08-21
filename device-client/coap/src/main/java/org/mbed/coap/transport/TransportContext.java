/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.transport;

/**
 * Interface to provide transport context information. For example SMS phone
 * number, security key, security certificate subject.
 *
 * @author szymon
 */
public interface TransportContext {

    public static final TransportContext NULL = new TransportContext() {    //NOPMD

        @Override
        public <T> T get(Enum<?> enumerator, Class<T> clazz) {
            return null;
        }

        @Override
        public Object get(Enum<?> enumerator) {
            return null;
        }

        @Override
        public String toString() {
            return "NULL-TransportContext";
        }

    };

    /**
     * Returns context parameter for given enumerator and type.
     *
     * @param enumerator parameter
     * @param clazz parameter class
     * @return casted value or null if requested parameter does not exists or
     * value can not be casted.
     */
    <T> T get(Enum<?> enumerator, Class<T> clazz);

    /**
     * Returns context parameter for given enumerator and type.
     *
     * @param enumerator parameter
     * @return associated value or null if requested parameter does not exists
     */
    Object get(Enum<?> enumerator);

}
