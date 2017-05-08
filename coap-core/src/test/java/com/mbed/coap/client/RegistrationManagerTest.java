/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
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
package com.mbed.coap.client;

import static com.mbed.coap.packet.MediaTypes.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.threeten.bp.Duration.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.server.MessageIdSupplierImpl;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import protocolTests.utils.TransportConnectorMock;

/**
 * Created by szymon
 */
public class RegistrationManagerTest {
    private CoapServer deviceSrv;
    private TransportConnectorMock trnsport;
    private ScheduledExecutorService scheduledExecutor = Mockito.mock(ScheduledExecutorService.class);

    @Before
    public void setUp() throws Exception {
        trnsport = new TransportConnectorMock();

        deviceSrv = CoapServerBuilder.newBuilder()
                .transport(trnsport)
                .midSupplier(new MessageIdSupplierImpl(0))
                .build().start();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void register_andScheduleUpdateBeforeExpiration() throws Exception {
        trnsport.when(newCoapPacket(1).post().uriPath("/rd").uriQuery("ep=stub-device-01&lt=7200").contFormat(CT_APPLICATION_LINK__FORMAT))
                .then(newCoapPacket(1).ack(Code.C201_CREATED).locPath("/stub/0001").maxAge(3600));
        RegistrationManager reg = new RegistrationManager(deviceSrv, URI.create("coap://localhost:5683/rd?ep=stub-device-01&lt=7200"), scheduledExecutor);

        //when
        reg.register();

        //then
        assertTrue(reg.isRegistered());
        verifyScheduledInSec(3600L - 30);
    }

    @Test
    public void register_shortLifetime() throws Exception {
        trnsport.when(newCoapPacket(1).post().uriPath("/rd").uriQuery("ep=stub-device-01&lt=59").contFormat(CT_APPLICATION_LINK__FORMAT))
                .then(newCoapPacket(1).ack(Code.C201_CREATED).locPath("/stub/0001").maxAge(59));
        RegistrationManager reg = new RegistrationManager(deviceSrv, URI.create("coap://localhost:5683/rd?ep=stub-device-01&lt=59"), scheduledExecutor);

        //when
        reg.register();

        //then
        assertTrue(reg.isRegistered());
        verifyScheduledInSec(54L);
    }

    @Test
    public void register_failFromServer() throws Exception {
        trnsport.when(newCoapPacket(1).post().uriPath("/rd").uriQuery("ep=stub-device-01&lt=7200").contFormat(CT_APPLICATION_LINK__FORMAT))
                .then(newCoapPacket(1).ack(Code.C400_BAD_REQUEST));
        RegistrationManager reg = new RegistrationManager(deviceSrv, URI.create("coap://localhost:5683/rd?ep=stub-device-01&lt=7200"), scheduledExecutor);

        //when
        reg.register();

        //then
        assertFalse(reg.isRegistered());
        verifyScheduledInSec(10L);
    }

    @Test
    public void register_fail_connection() throws Exception {
        trnsport.when(newCoapPacket(1).post().uriPath("/rd").uriQuery("ep=stub-device-01&lt=7200").contFormat(CT_APPLICATION_LINK__FORMAT))
                .thenThrow(new IOException());
        RegistrationManager reg = new RegistrationManager(deviceSrv, URI.create("coap://localhost:5683/rd?ep=stub-device-01&lt=7200"), scheduledExecutor);

        //when
        reg.register();

        //then
        assertFalse(reg.isRegistered());
        Runnable task = verifyScheduledInSec(10L);


        //run scheduled task
        trnsport.when(newCoapPacket(2).post().uriPath("/rd").uriQuery("ep=stub-device-01&lt=7200").contFormat(CT_APPLICATION_LINK__FORMAT))
                .then(newCoapPacket(2).ack(Code.C201_CREATED).locPath("/stub/0001").maxAge(3600));

        task.run();

        assertTrue(reg.isRegistered());
    }

    @Test
    public void update() throws Exception {
        //given
        RegistrationManager reg = registered();

        trnsport.when(newCoapPacket(2).post().uriPath("/stub/0001"))
                .then(newCoapPacket(2).ack(Code.C204_CHANGED).maxAge(101));

        //when
        runScheduledTask();

        //then
        assertTrue(reg.isRegistered());
        verifyScheduledInSec(71L);
    }

    @Test
    public void update_failedFromServer_immediately_reRegister() throws Exception {
        //given
        RegistrationManager reg = registered();

        trnsport.when(newCoapPacket(2).post().uriPath("/stub/0001"))
                .then(newCoapPacket(2).ack(Code.C404_NOT_FOUND));
        trnsport.when(newCoapPacket(3).post().uriPath("/rd").uriQuery("ep=stub-device-01&lt=100").contFormat(CT_APPLICATION_LINK__FORMAT))
                .then(newCoapPacket(3).ack(Code.C201_CREATED).locPath("/stub/0001").maxAge(102));

        //when
        runScheduledTask();

        //then
        assertTrue(reg.isRegistered());
        verifyScheduledInSec(72L);
    }

    @Test
    public void update_failedConnection_immediately_reRegister() throws Exception {
        //given
        RegistrationManager reg = registered();

        trnsport.when(newCoapPacket(2).post().uriPath("/stub/0001"))
                .thenThrow(new IOException());
        trnsport.when(newCoapPacket(3).post().uriPath("/rd").uriQuery("ep=stub-device-01&lt=100").contFormat(CT_APPLICATION_LINK__FORMAT))
                .then(newCoapPacket(3).ack(Code.C201_CREATED).locPath("/stub/0001").maxAge(102));

        //when
        runScheduledTask();

        //then
        assertTrue(reg.isRegistered());
        verifyScheduledInSec(72L);
    }

    @Test
    public void remove() throws Exception {
        //given
        RegistrationManager reg = registered();

        trnsport.when(newCoapPacket(2).delete().uriPath("/stub/0001"))
                .then(newCoapPacket(2).ack(Code.C202_DELETED));

        reg.removeRegistration();
        assertFalse(reg.isRegistered());
    }

    @Test
    public void nextRetryDelay() throws Exception {
        RegistrationManager reg = new RegistrationManager(deviceSrv, URI.create("coap://localhost:5683/rd?ep=stub-device-01&lt=100"), scheduledExecutor,
                /* min */ ofMinutes(2), /* max */ ofMinutes(10));

        assertEquals(ofMinutes(2), reg.nextDelay(ofMinutes(0)));
        assertEquals(ofMinutes(4), reg.nextDelay(ofMinutes(2)));
        assertEquals(ofMinutes(8), reg.nextDelay(ofMinutes(4)));

        //do not go over max
        assertEquals(ofMinutes(10), reg.nextDelay(ofMinutes(8)));
        assertEquals(ofMinutes(10), reg.nextDelay(ofMinutes(11)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void failWithWrongRegistrationDuration() throws Exception {
        new RegistrationManager(deviceSrv, URI.create("coap://localhost:5683/rd?ep=stub-device-01&lt=100"), scheduledExecutor,
                ofMinutes(3), ofMinutes(2));
    }

    @Test
    public void reRegister_after_disconnection() throws IOException {
        //given
        RegistrationManager reg = tcpRegistered();

        //when, disconnected
        trnsport.when(newCoapPacket().post().uriPath("/stub/0001"))
                .then(newCoapPacket().ack(Code.C204_CHANGED).maxAge(130));

        trnsport.mockOnConnected();

        //then
        assertTrue(reg.isRegistered());
        verifyScheduledInSec(100L);
    }

    private RegistrationManager registered() {
        trnsport.when(newCoapPacket(1).post().uriPath("/rd").uriQuery("ep=stub-device-01&lt=100").contFormat(CT_APPLICATION_LINK__FORMAT))
                .then(newCoapPacket(1).ack(Code.C201_CREATED).locPath("/stub/0001").maxAge(100));
        RegistrationManager reg = new RegistrationManager(deviceSrv, URI.create("coap://localhost:5683/rd?ep=stub-device-01&lt=100"), scheduledExecutor);

        reg.register();
        assertTrue(reg.isRegistered());

        return reg;
    }

    private RegistrationManager tcpRegistered() throws IOException {
        deviceSrv = CoapServerBuilder.newBuilderForTcp().transport(trnsport).build().start();

        trnsport.when(newCoapPacket(0).post().uriPath("/rd").uriQuery("ep=stub-device-01&lt=100").contFormat(CT_APPLICATION_LINK__FORMAT))
                .then(newCoapPacket(0).ack(Code.C201_CREATED).locPath("/stub/0001").maxAge(100));
        RegistrationManager reg = new RegistrationManager(deviceSrv, URI.create("coap://localhost:5683/rd?ep=stub-device-01&lt=100"), scheduledExecutor);

        reg.register();
        assertTrue(reg.isRegistered());

        return reg;
    }

    private Runnable verifyScheduledInSec(long delay) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduledExecutor).schedule(captor.capture(), eq(delay), eq(TimeUnit.SECONDS));

        return captor.getValue();
    }

    private void runScheduledTask() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduledExecutor).schedule(captor.capture(), anyLong(), eq(TimeUnit.SECONDS));

        captor.getValue().run();
    }

}