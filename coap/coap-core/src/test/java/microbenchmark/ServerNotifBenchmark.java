/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package microbenchmark;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.packet.Code;
import org.mbed.coap.packet.MessageType;
import org.mbed.coap.server.CoapExchange;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.server.CoapServerBuilder;
import org.mbed.coap.server.ObservationHandler;

/**
 * @author szymon
 */
public class ServerNotifBenchmark {

    ServerBenchmarkBase.FloodTransportStub trans;
    protected int MAX = 1000000;
    protected ExecutorService executor;
    //
    private byte[] reqData;
    private ByteBuffer buffer;
    private CoapServer server;
    private long stTime, endTime;

    @Before
    public void warmUp() throws CoapException, IOException {
        LogManager.getRootLogger().setLevel(Level.ERROR);
        CoapPacket coapReq = new CoapPacket(Code.C205_CONTENT, MessageType.Confirmable, null);
        coapReq.setMessageId(1234);
        coapReq.setToken(new byte[]{1, 2, 3, 4, 5});
        coapReq.headers().setMaxAge(4321L);
        coapReq.headers().setObserve(6328);
        reqData = coapReq.toByteArray();

        buffer = ByteBuffer.wrap(reqData);
        buffer.position(coapReq.toByteArray().length);

        executor = Executors.newScheduledThreadPool(1);
        trans = new ServerBenchmarkBase.FloodTransportStub(MAX, executor);
        server = CoapServerBuilder.newBuilder().transport(trans).duplicateMsgCacheSize(10000).build();
        server.start();
        server.setObservationHandler(new ObservationHandlerNull());
        System.out.println("MSG SIZE: " + reqData.length);
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        //Thread.sleep(4000);
    }

    @After
    public void coolDown() {
        System.out.println("RUN-TIME: " + (endTime - stTime) + "ms, MSG-PER-SEC: " + (MAX * 1000L / (endTime - stTime)));
        server.stop();
    }

    @Test
    public void notificatins_1000k() throws InterruptedException {
        stTime = System.currentTimeMillis();
        int mid = 0;
        for (int i = 0; i < MAX; i++) {
            //change MID
            reqData[2] = (byte) (mid >> 8);
            reqData[3] = (byte) (mid & 0xFF);

            if (trans.receive(buffer)) {
                mid++;
            }
        }
        trans.LATCH.await();
        endTime = System.currentTimeMillis();
    }

    private static class ObservationHandlerNull implements ObservationHandler {

        @Override
        public boolean hasObservation(byte[] token) {
            return true;
        }

        @Override
        public void callException(Exception ex) {
            ex.printStackTrace();
        }

        @Override
        public void call(CoapExchange t) {
            t.sendResponse();
        }
    }
}
