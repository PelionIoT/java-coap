package org.mbed.coap.transport;

import static org.junit.Assert.*;
import org.mbed.coap.transport.AbstractTransportContext;
import org.mbed.coap.transport.TransportContext;
import org.junit.Test;

/**
 *
 * @author szymon
 */
public class TransportContextTest {

    @Test
    public void test() {
        TransportContext tc = new MyContext(new MyContext2(null));

        assertEquals("address", tc.get(ContextEnum.ADDRESS, String.class));
        assertEquals("address", tc.get(ContextEnum.ADDRESS));
        assertEquals("address2", tc.get(ContextEnum2.ADDRESS2, String.class));

        assertNull(tc.get(ContextEnum2.ADDRESS2, Integer.class));
        assertNull(tc.get(ContextEnum.CERTIFICATE));
        assertNull(tc.get(ContextEnum3.ADDRESS3));

    }

    private static class MyContext extends AbstractTransportContext<ContextEnum> {

        public MyContext(TransportContext wrappedTransContext) {
            super(wrappedTransContext);
        }

        @Override
        protected Object getParameter(ContextEnum enumerator) {
            switch (enumerator) {
                case ADDRESS:
                    return "address";
                default:
                    return null;

            }
        }
    }

    private static class MyContext2 extends AbstractTransportContext<ContextEnum2> {

        public MyContext2(TransportContext wrappedTransContext) {
            super(wrappedTransContext);
        }

        @Override
        protected Object getParameter(ContextEnum2 enumerator) {
            switch (enumerator) {
                case ADDRESS2:
                    return "address2";
                default:
                    return null;

            }
        }
    }

    public enum ContextEnum {

        ADDRESS, CERTIFICATE
    }

    public enum ContextEnum2 {

        ADDRESS2
    }

    public enum ContextEnum3 {

        ADDRESS3
    }

}
