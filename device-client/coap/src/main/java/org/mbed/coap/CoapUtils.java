/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap;

import org.mbed.coap.utils.CoapCallback;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author szymon
 */
public class CoapUtils {

    private static CoapCallback nullCallback;

    public static String decodeString(byte[] byteString) {
        return new String(byteString, CoapConstants.DEFAULT_CHARSET);
    }

    public static byte[] encodeString(String payload) {
        if (payload == null || payload.length() == 0) {
            return new byte[0];
        }
        return payload.getBytes(CoapConstants.DEFAULT_CHARSET);
    }

    /**
     * Returns callback implementation that will ignore all calls
     */
    public synchronized static CoapCallback getCallbackNull() {
        if (nullCallback == null) {
            nullCallback = new CoapCallback() {

                @Override
                public void callException(Exception ex) {
                    // nothing to do
                }

                @Override
                public void call(CoapPacket t) {
                    // nothing to do
                }
            };
        }

        return nullCallback;
    }

    //TODO: refactor to own class
    public static interface PacketDropping {    //NOPMD

        boolean drop();
    }

    //TODO: refactor to own class
    public static class NoPacketDropping implements PacketDropping {

        @Override
        public boolean drop() {
            return false;
        }
    }

    //TODO: refactor to own class
    public static class ProbPacketDropping implements PacketDropping {

        private final byte probability; //0-100
        private final Random r = new Random();

        public ProbPacketDropping(byte probability) {
            if (probability < 0 || probability > 100) {
                throw new IllegalArgumentException("Value must be in range 0-100");
            }
            this.probability = probability;
        }

        @Override
        public boolean drop() {
            return (probability <= 0) ? false : (r.nextInt(100) < probability);
        }
    }

    //TODO: refactor to own class
    public static interface PacketDelay {   //NOPMD

        void delay() throws InterruptedException;
    }

    //TODO: refactor to own class
    public static class NoPacketDelay implements PacketDelay {

        @Override
        public void delay() {
            // nothing to do
        }
    }

    //TODO: refactor to own class
    public static class AvgPacketDelay implements PacketDelay {

        private static final Logger LOGGER = LoggerFactory.getLogger(AvgPacketDelay.class);
        private final float avgDelay;
        private final Random random = new Random();

        public AvgPacketDelay(float avgDelay) {
            this.avgDelay = avgDelay;
        }

        @Override
        public void delay() throws InterruptedException {
            if (this.avgDelay <= 0) {
                return;
            }

            double delayTime = avgDelay;
            delayTime += delayTime * (random.nextGaussian()) / 2;
            if (delayTime < 0) {
                delayTime = 0;
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Packet delay = " + delayTime + " ms");
            }
            Thread.sleep((long) delayTime);
        }
    }
}
