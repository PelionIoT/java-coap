package com.mbed.coap.packet;


import static org.junit.Assert.*;

abstract class CoapPacketTestBase {

    static void assertSimilar(CoapPacket cp1, CoapPacket cp2) {
        assertEquals(cp1.getMethod(), cp2.getMethod());
        assertEquals(cp1.getMessageType(), cp2.getMessageType());
        assertEquals(cp1.getCode(), cp2.getCode());
        assertEquals(cp1.getMessageId(), cp2.getMessageId());

        assertEquals(cp1.signalingOptions().getMaxMessageSize(), cp2.signalingOptions().getMaxMessageSize());
        assertEquals(cp1.signalingOptions().getBlockWiseTransfer(), cp2.signalingOptions().getBlockWiseTransfer());
        assertEquals(cp1.signalingOptions().getCustody(), cp2.signalingOptions().getCustody());
        assertEquals(cp1.signalingOptions().getAlternativeAddresses(), cp2.signalingOptions().getAlternativeAddresses());
        assertEquals(cp1.signalingOptions().getHoldOff(), cp2.signalingOptions().getHoldOff());
        assertEquals(cp1.signalingOptions().getBadCsmOption(), cp2.signalingOptions().getBadCsmOption());

        assertEquals(cp1.headers().getBlock1Req(), cp2.headers().getBlock1Req());
        assertEquals(cp1.headers().getBlock2Res(), cp2.headers().getBlock2Res());
        assertEquals(cp1.headers().getUriPath(), cp2.headers().getUriPath());
        assertEquals(cp1.headers().getUriAuthority(), cp2.headers().getUriAuthority());
        assertEquals(cp1.headers().getUriHost(), cp2.headers().getUriHost());
        assertEquals(cp1.headers().getUriQuery(), cp2.headers().getUriQuery());
        assertEquals(cp1.headers().getLocationPath(), cp2.headers().getLocationPath());
        assertEquals(cp1.headers().getLocationQuery(), cp2.headers().getLocationQuery());

        assertArrayEquals(cp1.headers().getAccept(), cp2.headers().getAccept());
        assertArrayEquals(cp1.headers().getIfMatch(), cp2.headers().getIfMatch());
        assertArrayEquals(cp1.headers().getEtagArray(), cp2.headers().getEtagArray());

        assertEquals(cp1.headers().getIfNonMatch(), cp2.headers().getIfNonMatch());
        assertEquals(cp1.headers().getContentFormat(), cp2.headers().getContentFormat());
        assertArrayEquals(cp1.headers().getEtag(), cp2.headers().getEtag());
        assertEquals(cp1.headers().getMaxAge(), cp2.headers().getMaxAge());
        assertEquals(cp1.headers().getObserve(), cp2.headers().getObserve());
        assertEquals(cp1.headers().getProxyUri(), cp2.headers().getProxyUri());
        assertArrayEquals(cp1.getToken(), cp2.getToken());
        assertEquals(cp1.headers().getUriPort(), cp2.headers().getUriPort());

        assertEquals(cp1.getPayloadString(), cp2.getPayloadString());
        assertEquals(1, cp2.getVersion());

        assertEquals(cp1.getRemoteAddress(), cp2.getRemoteAddress());
    }

}
