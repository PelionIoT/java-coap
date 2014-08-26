package microbenchmark;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.MessageType;
import org.mbed.coap.Method;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.transport.TransportConnector;
import org.mbed.coap.transport.TransportContext;
import org.mbed.coap.transport.TransportReceiver;
import org.mbed.coap.utils.SimpleCoapResource;

/**
 *
 * @author szymon
 */
public abstract class ServerBenchmarkBase {

    FloodTransportStub trans;
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
        CoapPacket coapReq = new CoapPacket(Method.GET, MessageType.Confirmable, "/path1/sub2/sub3", null);
        coapReq.setMessageId(1234);
        coapReq.setToken(new byte[]{1, 2, 3, 4, 5});
        coapReq.headers().setMaxAge(4321L);
        reqData = coapReq.toByteArray();

        buffer = ByteBuffer.wrap(reqData);
        buffer.position(coapReq.toByteArray().length);

        server = CoapServer.newBuilder().transport(trans).executor(executor).duplicateMsgCacheSize(10000).build();
        server.addRequestHandler("/path1/sub2/sub3", new SimpleCoapResource("1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"));
        server.start();
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
    public void server_multi_1000k() throws InterruptedException {
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

    static class FloodTransportStub implements TransportConnector {

        private TransportReceiver udpReceiver;

        private final InetSocketAddress[] addrArr;
        final CountDownLatch LATCH;

        public FloodTransportStub(int max) {
            this(max, (int) Math.ceil(max / (double) 0xFFFF)); //no message duplication guaranted
        }

        public FloodTransportStub(int max, int maxAddr) {
            this.LATCH = new CountDownLatch(max);
            addrArr = new InetSocketAddress[maxAddr];
            for (int i = 0; i < maxAddr; i++) {
                addrArr[i] = new InetSocketAddress("localhost", 5684 + i);
            }
        }

        @Override
        public void start(TransportReceiver udpReceiver) throws IOException {
            this.udpReceiver = udpReceiver;
        }

        @Override
        public void stop() {
            this.udpReceiver = null;
        }

        @Override
        public void send(byte[] data, int len, InetSocketAddress destinationAddress, TransportContext transContext) throws IOException {
            LATCH.countDown();
        }

        @Override
        public InetSocketAddress getLocalSocketAddress() {
            return addrArr[0];
        }
        private int addIndex = 0;

        public boolean receive(ByteBuffer data) {
            udpReceiver.onReceive(addrArr[addIndex++ % addrArr.length], data, null);
            return addIndex % addrArr.length == 0;
        }
    }
}
