package org.mbed.coap.udp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * IPv6 traffic-class verification class, not ment for regular unit testing.
 * 
 * @author szymon
 */
public class TrafficClassVerifier {
    
    private static final byte[] DATA = new byte[]{1,2,3};
    private static final String DESTINATION = "::1";
    
    @Test
    public void datagramSocket() throws Exception {
        DatagramSocket socket = new DatagramSocket(0);

        socket.setTrafficClass(0x10);
        socket.send(new DatagramPacket(DATA, 3, InetAddress.getByName(DESTINATION), 5683));
        System.out.println("VERIFY IN WIRESHARK THAT PACKET'S TRAFFIC-CLASS IS SET!");
        
        assertEquals(0x10, socket.getTrafficClass());
        socket.close();
    }
    
    @Test
    public void datagramChannel() throws Exception {
        DatagramChannel channel = DatagramChannel.open() ;        
        channel.socket().bind(new InetSocketAddress(0));
        
        channel.socket().setTrafficClass(0x10);
        channel.send(ByteBuffer.wrap(DATA), new InetSocketAddress(DESTINATION, 5683));
        System.out.println("VERIFY IN WIRESHARK THAT PACKET'S TRAFFIC-CLASS IS SET!");
        
        assertEquals(0x10, channel.socket().getTrafficClass());
        channel.close();
    }
}
