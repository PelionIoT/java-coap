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
package com.mbed.coap.server.messaging;

import static java.util.concurrent.CompletableFuture.*;
import static org.junit.jupiter.api.Assertions.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

public class ObservationMapperTest {
    private final ObservationMapper observationMapper = new ObservationMapper();

    @Test
    public void ackResponseToObservation() {
        CompletableFuture<CoapPacket> resp = observationMapper.apply(
                newCoapPacket(LOCAL_5683).mid(13).token(918).con(Code.C205_CONTENT).payload("OK").obs(19).build(),
                separateResponse -> completedFuture(true)
        );

        assertEquals(newCoapPacket(LOCAL_5683).emptyAck(13), resp.join());
    }

    @Test
    public void resetResponseToObservation() {
        CompletableFuture<CoapPacket> resp = observationMapper.apply(
                newCoapPacket(LOCAL_5683).mid(13).token(918).con(Code.C205_CONTENT).payload("OK").obs(19).build(),
                separateResponse -> completedFuture(false)
        );

        assertEquals(newCoapPacket(LOCAL_5683).reset(13), resp.join());
    }

    @Test
    public void noResponseToNonObservation() {
        CompletableFuture<CoapPacket> resp = observationMapper.apply(
                newCoapPacket(LOCAL_5683).mid(13).token(918).non(Code.C205_CONTENT).payload("OK").obs(19).build(),
                separateResponse -> completedFuture(false)
        );

        assertNull(resp.join());
    }
}