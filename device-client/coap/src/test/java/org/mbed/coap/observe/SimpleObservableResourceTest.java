package org.mbed.coap.observe;

import org.mbed.coap.server.CoapServerBuilder;

import java.io.IOException;
import java.net.InetSocketAddress;
import org.junit.After;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.client.CoapClient;
import org.mbed.coap.client.CoapClientBuilder;
import org.mbed.coap.client.ObservationListener;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.exception.ObservationTerminatedException;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.test.InMemoryTransport;
import org.mbed.coap.test.ObservationTest;
import org.mbed.coap.transmission.SingleTimeout;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 *
 * @author szymon
 */
public class SimpleObservableResourceTest {

    private SimpleObservableResource obsResource;
    private CoapServer server;
    private CoapClient client;

    @Before
    public void setUp() throws IOException {
        server = CoapServerBuilder.newBuilder().transport(new InMemoryTransport(5683))
                .timeout(new SingleTimeout(500)).build();

        obsResource = new SimpleObservableResource("", server);

        server.addRequestHandler("/obs", obsResource);
        server.start();

        client = CoapClientBuilder.newBuilder(InMemoryTransport.createAddress(5683))
                .transport(new InMemoryTransport())
                .timeout(1000).build();
    }

    @After
    public void tearDown() {
        server.stop();
    }

    @Test
    public void testDeliveryListener_success() throws CoapException {

        ObservationTest.SyncObservationListener obsListener = new ObservationTest.SyncObservationListener();
        assertNotNull(client.resource("/obs").sync().observe(obsListener));

        NotificationDeliveryListener delivListener = mock(NotificationDeliveryListener.class);
        obsResource.setBody("", delivListener);
        verify(delivListener, timeout(1000)).onSuccess(any(InetSocketAddress.class));
        verify(delivListener, never()).onFail(any(InetSocketAddress.class));
        verify(delivListener, never()).onNoObservers();

    }

    @Test
    public void testDeliveryListener_fail() throws CoapException {
        ObservationListener obsListener = mock(ObservationListener.class);
        doThrow(new ObservationTerminatedException(null)).when(obsListener).onObservation(any(CoapPacket.class));

        assertNotNull(client.resource("/obs").sync().observe(obsListener));

        NotificationDeliveryListener delivListener = mock(NotificationDeliveryListener.class);
        obsResource.setBody("", delivListener);
        verify(delivListener, timeout(1000)).onFail(any(InetSocketAddress.class));
        verify(delivListener, never()).onSuccess(any(InetSocketAddress.class));
        verify(delivListener, never()).onNoObservers();
    }

    @Test
    public void testDeliveryListener_timeout() throws CoapException {
        ObservationListener obsListener = mock(ObservationListener.class);

        assertNotNull(client.resource("/obs").sync().observe(obsListener));

        client.close();

        NotificationDeliveryListener delivListener = mock(NotificationDeliveryListener.class);
        obsResource.setBody("", delivListener);
        verify(delivListener, timeout(3000)).onFail(any(InetSocketAddress.class));
        verify(delivListener, never()).onSuccess(any(InetSocketAddress.class));
        verify(delivListener, never()).onNoObservers();
    }

    @Test(expected = NullPointerException.class)
    public void setBodyWithNull() throws CoapException {
        obsResource.setBody("", null);
    }

    @Test
    public void setBodyWith_noObservers() throws CoapException {
        NotificationDeliveryListener delivListener = mock(NotificationDeliveryListener.class);
        obsResource.setBody("", delivListener);

        verify(delivListener).onNoObservers();
        verify(delivListener, never()).onFail(any(InetSocketAddress.class));
        verify(delivListener, never()).onSuccess(any(InetSocketAddress.class));
    }
}
