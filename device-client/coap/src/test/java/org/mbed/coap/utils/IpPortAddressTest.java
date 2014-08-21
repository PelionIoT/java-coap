package org.mbed.coap.utils;

import org.mbed.coap.utils.IpPortAddress;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author szymon
 */
public class IpPortAddressTest {

    @Test
    public void test() throws UnknownHostException {
        IpPortAddress addr = new IpPortAddress(new InetSocketAddress("127.0.0.1", 5683));

        assertArrayEquals(addr.getIp(), new byte[]{127, 0, 0, 1});
        assertEquals(addr.getPort(), 5683);
        assertEquals(addr, new IpPortAddress(new byte[]{127, 0, 0, 1}, 5683));
        assertEquals(addr.hashCode(), new IpPortAddress(new byte[]{127, 0, 0, 1}, 5683).hashCode());

        assertEquals(addr, new IpPortAddress(new InetSocketAddress(InetAddress.getByAddress("perse", new byte[]{127, 0, 0, 1}), 5683)));
        
        assertFalse(addr.equals(null));
        assertFalse(addr.equals("dsa"));
        assertFalse(addr.equals(new IpPortAddress(new byte[]{127, 0, 0, 46}, 5683)));
        assertFalse(addr.equals(new IpPortAddress(new byte[]{127, 0, 0, 1}, 1)));
        
        assertEquals(new InetSocketAddress("127.0.0.1", 5683), new IpPortAddress(new byte[]{127, 0, 0, 1}, 5683).toInetSocketAddress() );
    }

    @SuppressWarnings("unused")
    @Test(expected = IllegalArgumentException.class)
    public void wrongIp() {
        new IpPortAddress(new byte[8], 65);
    }
    
    @SuppressWarnings("unused")
    @Test(expected = IllegalArgumentException.class)
    public void wrongPort() {
        new IpPortAddress(new byte[4], 545456);
    }

    @Test
    public void toStringTest() {
        //v6
        IpPortAddress addr = new IpPortAddress(new InetSocketAddress("[2001::1:0:1234]", 4321));
        assertEquals("[2001::1:0:1234]:4321", addr.toString());
        assertEquals("[2001::1:0:1234]:4321", new IpPortAddress(new InetSocketAddress("[2001:0000:0000:0000:0000:0001:0000:1234]", 4321)).toString() );

        //v4
        addr = new IpPortAddress(new InetSocketAddress("127.0.0.1", 61616));
        assertEquals("127.0.0.1:61616", addr.toString());
    }
}
