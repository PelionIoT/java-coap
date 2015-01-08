/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.transport;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author szymon
 */
public class TransportContextTest {

    @Test
    public void test() {
        TransportContext tc = new TransportContext(2, "node001", "dupa".getBytes(), "+358000111222");

        assertEquals("+358000111222", tc.getMsisdn());
        assertEquals("node001", tc.getCertificateCN());
        assertArrayEquals("dupa".getBytes(), tc.getPreSharedKeyID());

        //null
        assertNull(TransportContext.NULL.getCertificateCN());
        assertNull(TransportContext.NULL.getPreSharedKeyID());
        assertNull(TransportContext.NULL.getTrafficClass());
    }

    @Test
    public void testImmutable() throws Exception {
        TransportContext tc = new TransportContext(2, "node001", "dupa".getBytes(), "+358000111222");

        assertEquals(new TransportContext(100, "node002", "2".getBytes(), "+48000111222"),
                tc.withCertificateCN("node002").withMsisdn("+48000111222").withPreSharedKeyID("2".getBytes()).withTrafficClass(100));

        assertEquals(new TransportContext(2, "node001", "dupa".getBytes(), "+358000111222"), tc);
        assertEquals(new TransportContext(2, "node001", "dupa".getBytes(), "+358000111222").hashCode(), tc.hashCode());
    }

}
