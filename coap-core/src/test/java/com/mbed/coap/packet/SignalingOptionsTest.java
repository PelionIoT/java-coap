/**
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mbed.coap.packet;

import static org.junit.Assert.*;
import com.mbed.coap.exception.CoapException;
import java.io.IOException;
import java.nio.ByteBuffer;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;


public class SignalingOptionsTest {

    @Test
    public void testEmpty() throws IOException, CoapException {
        SignalingOptions options = new SignalingOptions();

        assertNull(options.serializeOption2());
        assertNull(options.serializeOption4());
    }

    @Test
    public void testCSM() throws IOException, CoapException {
        SignalingOptions opt = new SignalingOptions();
        opt.setMaxMessageSize(4);
        opt.setBlockWiseTransfer(true);

        SignalingOptions opt2 = serializeAndDeserialize(opt, Code.C701_CSM);

        assertEquals(opt, opt2);
        assertEquals(4, opt2.getMaxMessageSize().intValue());
        assertTrue(opt2.getBlockWiseTransfer());
    }

    private SignalingOptions serializeAndDeserialize(SignalingOptions opt, Code c701Csm) {
        SignalingOptions opt2 = new SignalingOptions();
        opt2.parse(2, opt.serializeOption2(), c701Csm);
        opt2.parse(4, opt.serializeOption4(), c701Csm);
        return opt2;
    }

    @Test
    public void testPing() throws IOException, CoapException {
        SignalingOptions opt = new SignalingOptions();
        opt.setCustody(true);

        SignalingOptions opt2 = serializeAndDeserialize(opt, Code.C702_PING);

        assertEquals(opt, opt2);
        assertTrue(opt2.getCustody());
    }

    @Test
    public void testRelease() throws IOException, CoapException {
        SignalingOptions opt = new SignalingOptions();
        opt.setAlternativeAddress("0.0.0.0:1");
        opt.setHoldOff(3);

        SignalingOptions opt2 = serializeAndDeserialize(opt, Code.C704_RELEASE);

        assertEquals(opt, opt2);
        assertEquals("0.0.0.0:1", opt2.getAlternativeAddress());
    }

    @Test
    public void testAbort() throws IOException, CoapException {
        SignalingOptions opt = new SignalingOptions();
        opt.setBadCsmOption(6);

        SignalingOptions opt2 = serializeAndDeserialize(opt, Code.C705_ABORT);

        assertEquals(opt, opt2);
        assertEquals(6, opt2.getBadCsmOption().intValue());
    }

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Test
    public void testPutCSMOption() throws IOException, CoapException {
        SignalingOptions options = new SignalingOptions();

        options.parse(2, ByteBuffer.allocate(4).putInt(2).array(), Code.C701_CSM);

        assertEquals(2, options.getMaxMessageSize().intValue());
        assertEquals(null, options.getBlockWiseTransfer());
        assertEquals(null, options.getCustody());
        assertEquals(null, options.getAlternativeAddress());
        assertEquals(null, options.getHoldOff());
        assertEquals(null, options.getBadCsmOption());

        options.parse(4, null, Code.C701_CSM);

        assertEquals(2, options.getMaxMessageSize().intValue());
        assertTrue(options.getBlockWiseTransfer());
        assertEquals(null, options.getCustody());
        assertEquals(null, options.getAlternativeAddress());
        assertEquals(null, options.getHoldOff());
        assertEquals(null, options.getBadCsmOption());

        System.out.println(options);
        assertEquals(" MaxMsgSz:2 Blocks", options.toString());

        //Try putting non csm specific option
        expectedEx.expect(IllegalStateException.class);
        expectedEx.expectMessage("Other than 7.04 specific signaling option already set");
        options.parse(4, ByteBuffer.allocate(1).put((byte) 5).array(), Code.C704_RELEASE);
    }

    @Test
    public void testPutPingOption() throws IOException, CoapException {
        SignalingOptions options = new SignalingOptions();

        options.parse(2, null, Code.C702_PING);

        assertEquals(null, options.getMaxMessageSize());
        assertEquals(null, options.getBlockWiseTransfer());
        assertTrue(options.getCustody());
        assertEquals(null, options.getAlternativeAddress());
        assertEquals(null, options.getHoldOff());
        assertEquals(null, options.getBadCsmOption());

        System.out.println(options);
        assertEquals(" Custody", options.toString());

        //should work also with pong
        options.setCustody(false);
        assertFalse(options.getCustody());

        options.parse(2, null, Code.C703_PONG);

        assertEquals(null, options.getMaxMessageSize());
        assertEquals(null, options.getBlockWiseTransfer());
        assertTrue(options.getCustody());
        assertEquals(null, options.getAlternativeAddress());
        assertEquals(null, options.getHoldOff());
        assertEquals(null, options.getBadCsmOption());
    }

    @Test
    public void testPutReleaseOption() throws IOException, CoapException {
        SignalingOptions options = new SignalingOptions();

        options.parse(2, "127.0.0.1:5555".getBytes(), Code.C704_RELEASE);
        System.out.println(options);

        assertEquals(null, options.getMaxMessageSize());
        assertEquals(null, options.getBlockWiseTransfer());
        assertEquals(null, options.getCustody());
        assertEquals(null, options.getHoldOff());
        assertEquals(null, options.getBadCsmOption());
        assertEquals("127.0.0.1:5555", options.getAlternativeAddress());

        //        options.parse(2, "127.0.0.1:7777".getBytes(), Code.C704_RELEASE);
        //
        //        assertEquals(2, options.getAlternativeAddresses().size());
        //        assertEquals("127.0.0.1:5555", options.getAlternativeAddresses().get(0));
        //        assertEquals("127.0.0.1:7777", options.getAlternativeAddresses().get(1));
        //        assertEquals(null, options.getMaxMessageSize());
        //        assertEquals(null, options.getBlockWiseTransfer());
        //        assertEquals(null, options.getCustody());
        //        assertEquals(null, options.getHoldOff());
        //        assertEquals(null, options.getBadCsmOption());

        options.parse(4, ByteBuffer.allocate(1).put((byte) 5).array(), Code.C704_RELEASE);

        assertEquals("127.0.0.1:5555", options.getAlternativeAddress());
        assertEquals(null, options.getMaxMessageSize());
        assertEquals(null, options.getBlockWiseTransfer());
        assertEquals(null, options.getCustody());
        assertEquals(5, options.getHoldOff().intValue());
        assertEquals(null, options.getBadCsmOption());

        System.out.println(options);
        assertEquals(" AltAdr:127.0.0.1:5555 Hold-Off:5", options.toString());

        //        expectedEx.expect(IllegalArgumentException.class);
        //        expectedEx.expectMessage("Illegal Alternative-Address size: 300");
        //        options.put(2,
        //                ("127.0.0.1:7777asdfgh127.0.0.1:7777asdfgh127.0.0.1:7777asdfgh127.0.0.1:7777asdfgh127.0.0.1:7777asdfgh" +
        //                        "127.0.0.1:7777asdfgh127.0.0.1:7777asdfgh127.0.0.1:7777asdfgh127.0.0.1:7777asdfgh127.0.0.1:7777asdfgh" +
        //                        "127.0.0.1:7777asdfgh127.0.0.1:7777asdfgh127.0.0.1:7777asdfgh127.0.0.1:7777asdfgh127.0.0.1:7777asdfgh").getBytes(),
        //                Code.C704_RELEASE);
    }

    @Test
    public void testPutAbortOption() throws IOException, CoapException {
        SignalingOptions options = new SignalingOptions();

        options.parse(2, ByteBuffer.allocate(1).put((byte) 7).array(), Code.C705_ABORT);

        assertEquals(null, options.getMaxMessageSize());
        assertEquals(null, options.getBlockWiseTransfer());
        assertEquals(null, options.getCustody());
        assertEquals(null, options.getAlternativeAddress());
        assertEquals(null, options.getHoldOff());
        assertEquals(7, options.getBadCsmOption().intValue());

        System.out.println(options);
        assertEquals(" Bad-CSM:7", options.toString());
    }

    @Test
    public void testTooBigMaxMessageSize() throws IOException, CoapException {
        SignalingOptions options = new SignalingOptions();

        expectedEx.expect(IllegalArgumentException.class);
        expectedEx.expectMessage("Illegal Max-Message-Size argument: ");
        options.setMaxMessageSize(0x100000000L);
    }

    @Test
    public void testNegativeMaxMessageSize() throws IOException, CoapException {
        SignalingOptions options = new SignalingOptions();

        expectedEx.expect(IllegalArgumentException.class);
        expectedEx.expectMessage("Illegal Max-Message-Size argument: -1");
        options.setMaxMessageSize(-1L);
    }

    @Test
    public void testZeroMessageSize() {
        SignalingOptions options = new SignalingOptions();
        //TODO: lower value of max message size is not defined in current draft,
        // however values less than some small value (16 bytes for example) or
        // max-message-size == 0 absolutely useless. This test should be changed
        // and zero max message size should throw exception when draft will have
        // lower limit.
        //        expectedEx.expect(IllegalArgumentException.class);
        //        expectedEx.expectMessage("Illegal Max-Message-Size argument: -5");
        options.setMaxMessageSize(0);
        assertEquals(options.getMaxMessageSize().longValue(), 0);
    }

    @Test
    public void testTooBigHoldOff() throws IOException, CoapException {
        SignalingOptions options = new SignalingOptions();

        expectedEx.expect(IllegalArgumentException.class);
        expectedEx.expectMessage("Illegal Hold-Off argument: ");
        options.setHoldOff(0x1FFF);
    }

    @Test
    public void testNegativeHoldOff() throws IOException, CoapException {
        SignalingOptions options = new SignalingOptions();

        expectedEx.expect(IllegalArgumentException.class);
        expectedEx.expectMessage("Illegal Hold-Off argument: -5");
        options.setHoldOff(-5);
    }

    @Test
    public void testIllegalMaxMessageSizeState() throws IOException, CoapException {
        SignalingOptions options = new SignalingOptions();
        options.setHoldOff(2);

        expectedEx.expect(IllegalStateException.class);
        expectedEx.expectMessage("Other than 7.01 specific signaling option already set");
        options.setMaxMessageSize(5);
    }

    @Test
    public void testIllegalBlockWiseTransferState() throws IOException, CoapException {
        SignalingOptions options = new SignalingOptions();
        options.setHoldOff(2);

        expectedEx.expect(IllegalStateException.class);
        expectedEx.expectMessage("Other than 7.01 specific signaling option already set");
        options.setBlockWiseTransfer(true);
    }

    @Test
    public void testIllegalCustodyState() throws IOException, CoapException {
        SignalingOptions options = new SignalingOptions();
        options.setHoldOff(2);

        expectedEx.expect(IllegalStateException.class);
        expectedEx.expectMessage("Other than 7.02 or 7.03 specific signaling option already set");
        options.setCustody(true);
    }

    @Test
    public void testIllegalAlternativeAddressState() throws IOException, CoapException {
        SignalingOptions options = new SignalingOptions();
        options.setCustody(true);

        expectedEx.expect(IllegalStateException.class);
        expectedEx.expectMessage("Other than 7.04 specific signaling option already set");
        options.setAlternativeAddress(null);
    }

    @Test
    public void testIllegalHoldOffState() throws IOException, CoapException {
        SignalingOptions options = new SignalingOptions();
        options.setCustody(true);

        expectedEx.expect(IllegalStateException.class);
        expectedEx.expectMessage("Other than 7.04 specific signaling option already set");
        options.setHoldOff(2);
    }

    @Test
    public void testIllegalBadCsmOptionState() throws IOException, CoapException {
        SignalingOptions options = new SignalingOptions();
        options.setCustody(true);

        expectedEx.expect(IllegalStateException.class);
        expectedEx.expectMessage("Other than 7.05 specific signaling option already set");
        options.setBadCsmOption(2);
    }

    @Test
    public void equalsAndHashTest() throws Exception {
        EqualsVerifier.forClass(SignalingOptions.class).suppress(Warning.NONFINAL_FIELDS).usingGetClass().verify();

        assertFalse(new SignalingOptions().equals(null));
    }
}
