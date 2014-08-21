package org.mbed.coap.utils;

import org.mbed.coap.utils.ByteArrayBackedInputStream;
import org.mbed.coap.utils.ByteArrayBackedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 *
 * @author szymon
 */
public class ByteArrayBackedStreamTest {

    @Test
    public void output() throws IOException {
        @SuppressWarnings("resource")
        ByteArrayBackedOutputStream stream = new ByteArrayBackedOutputStream();

        stream.write(1);
        stream.write(2);
        stream.write(new byte[]{3, 4});
        stream.write(new byte[]{0, 0, 5, 6, 0}, 2, 2);
        assertEquals(6, stream.size());
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6}, stream.toByteArray());
    }

    @Test
    public void input() throws Exception {
        @SuppressWarnings("resource")
        ByteArrayBackedInputStream stream = new ByteArrayBackedInputStream(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});

        assertEquals(10, stream.available());
        assertEquals(1, stream.read());
        assertEquals(2, stream.read());
        byte[] temp = new byte[7];
        stream.read(temp);
        assertArrayEquals(new byte[]{3, 4, 5, 6, 7, 8, 9}, temp);
        assertEquals(1, stream.available());
        assertEquals(10, stream.read());
        assertEquals(0, stream.available());

        assertEquals(0, stream.read(new byte[0]));
    }

    @Test(expected = EOFException.class)
    public void input_fail() throws Exception {
        @SuppressWarnings("resource")
        ByteArrayBackedInputStream stream = new ByteArrayBackedInputStream(new byte[]{1});

        assertEquals(1, stream.read());
        assertEquals(0, stream.available());
        stream.read();
    }

    @Test(expected = EOFException.class)
    public void input_fail2() throws Exception {
        @SuppressWarnings("resource")
        ByteArrayBackedInputStream stream = new ByteArrayBackedInputStream(new byte[]{1});

        byte[] temp = new byte[2];
        stream.read(temp);
    }

    @Test(expected = EOFException.class)
    public void input_fail3() throws Exception {
        @SuppressWarnings("resource")
        ByteArrayBackedInputStream stream = new ByteArrayBackedInputStream(new byte[]{1, 2, 3, 4});

        assertEquals(3, stream.skip(3));
        stream.skip(2);
    }
}
