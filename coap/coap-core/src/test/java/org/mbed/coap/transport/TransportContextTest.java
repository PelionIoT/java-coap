/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package org.mbed.coap.transport;

import static org.testng.Assert.*;
import java.io.UnsupportedEncodingException;
import org.testng.annotations.Test;

/**
 * @author szymon
 */
public class TransportContextTest {

    @Test
    public void test() {
        TransportContext tc = new TransportContext(2, "node001", "dupa".getBytes(), "+358000111222", null);

        assertEquals("+358000111222", tc.getMsisdn());
        assertEquals("node001", tc.getCertificateCN());
        assertEquals("dupa".getBytes(), tc.getPreSharedKeyID());

        //null
        assertNull(TransportContext.NULL.getCertificateCN());
        assertNull(TransportContext.NULL.getPreSharedKeyID());
        assertNull(TransportContext.NULL.getTrafficClass());
    }

    @Test
    public void testImmutable() throws Exception {
        TransportContext tc = new TransportContext(2, "node001", "dupa".getBytes(), "+358000111222", null);

        assertEquals(new TransportContext(100, "node002", "2".getBytes(), "+48000111222", null),
                tc.withCertificateCN("node002").withMsisdn("+48000111222").withPreSharedKeyID("2".getBytes()).withTrafficClass(100));

        assertEquals(new TransportContext(2, "node001", "dupa".getBytes(), "+358000111222", null), tc);
        assertEquals(new TransportContext(2, "node001", "dupa".getBytes(), "+358000111222", null).hashCode(), tc.hashCode());
    }

    @Test
    public void toStringMentionsPskIdOrCertSubjectNameIfAvailable() throws UnsupportedEncodingException {
        assertEquals("TransportContext [trafficClass=null, certificateCN=null, preSharedKeyId=null, msisdn=null, deviceCert=null]", new TransportContext(null, null, null, null, null).toString());
        assertEquals("TransportContext [trafficClass=null, certificateCN=cname, preSharedKeyId=null, msisdn=null, deviceCert=null]", new TransportContext(null, "cname", null, null, null).toString());
        assertEquals("TransportContext [trafficClass=null, certificateCN=null, preSharedKeyId=[112, 115, 107, 45, 105, 100], msisdn=null, deviceCert=null]", new TransportContext(null, null, "psk-id".getBytes("UTF-8"), null, null).toString());
    }

    @Test
    public void toStringMentionsDeviceCertIfAvailable() {
        assertEquals("TransportContext [trafficClass=null, certificateCN=null, preSharedKeyId=null, msisdn=null, deviceCert=null]", new TransportContext(null, null, null, null, null).toString());
        assertEquals("TransportContext [trafficClass=null, certificateCN=null, preSharedKeyId=null, msisdn=null, deviceCert=[1, 2]]", new TransportContext(null, null, null, null, new byte[] {1,2,}).toString());
    }

}
