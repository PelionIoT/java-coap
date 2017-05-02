/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package microbenchmark;

import static com.mbed.coap.transport.AbstractTransportConnector.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Method;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.transport.TransportConnector;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.transport.TransportReceiver;
import com.mbed.coap.utils.SimpleCoapResource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author szymon
 */
public class ServerBenchmark {

    private byte[] reqData;
    private ByteBuffer buffer;
    private SynchTransportStub trans;
    private CoapServer server;
    private long stTime, endTime;
    private static final int MAX = 100000;

    @Before
    public void warmUp() throws CoapException, IOException {
        LogManager.getRootLogger().setLevel(Level.ERROR);
        CoapPacket coapReq = new CoapPacket(Method.GET, MessageType.Confirmable, "/path1/sub2/sub3", null);
        coapReq.setMessageId(1234);
        coapReq.setToken(new byte[]{1, 2, 3, 4, 5});
        coapReq.headers().setMaxAge(4321L);
        reqData = coapReq.toByteArray();

        buffer = ByteBuffer.wrap(reqData);
        buffer.position(coapReq.toByteArray().length);

        trans = new SynchTransportStub();
        server = CoapServerBuilder.newBuilder().transport(trans).build();
        server.addRequestHandler("/path1/sub2/sub3", new SimpleCoapResource("1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"));
        server.start();
        System.out.println("MSG SIZE: " + reqData.length);
    }

    @After
    public void coolDown() {
        System.out.println(String.format("RUN-TIME: %dms, MSG-PER-SEC: %d", (endTime - stTime), (MAX * 1000 / (endTime - stTime))));
        server.stop();
    }

    @Test
    public void server_100k() throws InterruptedException {
        stTime = System.currentTimeMillis();
        for (int i = 0; i < MAX; i++) {
            //change MID
            reqData[2] = (byte) (i >> 8);
            reqData[3] = (byte) (i & 0xFF);
            //send and wait for resp
            trans.receive(buffer);
        }
        endTime = System.currentTimeMillis();
    }

    private static class SynchTransportStub implements TransportConnector {

        private TransportReceiver udpReceiver;
        private Executor executor = Executors.newSingleThreadExecutor();
        private final InetSocketAddress addr = new InetSocketAddress("localhost", 5683);
        private boolean sendCalled = false;

        @Override
        public void start(TransportReceiver udpReceiver) throws IOException {
            this.udpReceiver = udpReceiver;
        }

        @Override
        public void stop() {
            this.udpReceiver = null;
        }

        @Override
        public synchronized void send(byte[] data, int len, InetSocketAddress destinationAddress, TransportContext transContext) throws IOException {
            sendCalled = true;
            this.notifyAll();
        }

        @Override
        public InetSocketAddress getLocalSocketAddress() {
            return addr;
        }

        public synchronized void receive(ByteBuffer data) throws InterruptedException {
            byte[] data1 = createCopyOfPacketData(data, data.position());
            executor.execute(() -> udpReceiver.onReceive(addr, data1, null));
            while (!sendCalled) {
                this.wait();
            }
        }
    }
}
