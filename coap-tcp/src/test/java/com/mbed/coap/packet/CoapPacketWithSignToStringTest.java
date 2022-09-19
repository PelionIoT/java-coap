/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
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

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;


public class CoapPacketWithSignToStringTest {

    @Test
    public void toString_withCapabilities() {
        CoapPacket cp = new CoapPacket(Code.C701_CSM, null, null);
        SignallingHeaderOptions headers = new SignallingHeaderOptions();
        cp.setHeaderOptions(headers);

        headers.putSignallingOptions(SignalingOptions.capabilities(2000, true));
        assertEquals("701 MID:0 MaxMsgSz:2000 Blocks", cp.toString());

        headers.putSignallingOptions(SignalingOptions.capabilities(2000, false));
        assertEquals("701 MID:0 MaxMsgSz:2000", cp.toString());


        SignalingOptions signalingOptions = new SignalingOptions();
        signalingOptions.setBlockWiseTransfer(true);
        headers.putSignallingOptions(signalingOptions);
        assertEquals("701 MID:0 Blocks", cp.toString());
    }
}
