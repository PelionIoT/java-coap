/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.utils;

import java.io.OutputStream;
import java.util.Arrays;

/**
 * Non blocking byte array output stream.
 *
 * @author szymon
 */
public class ByteArrayBackedOutputStream extends OutputStream {

    protected byte[] buffer;
    protected int position;

    public ByteArrayBackedOutputStream() {
        this(64);
    }

    public ByteArrayBackedOutputStream(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Negative initial size: " + size);
        }
        buffer = new byte[size];
    }

    @Override
    public void write(int b) {
        int newPosition = position + 1;
        if (newPosition > buffer.length) {
            buffer = Arrays.copyOf(buffer, Math.max(buffer.length << 1, newPosition));
        }
        buffer[position] = (byte) b;
        position = newPosition;
    }

    @Override
    public void write(byte[] b, int off, int len) {
        if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }
        int newPosition = position + len;
        if (newPosition > buffer.length) {
            buffer = Arrays.copyOf(buffer, Math.max(buffer.length << 1, newPosition));
        }
        System.arraycopy(b, off, buffer, position, len);
        position = newPosition;
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(buffer, position);
    }

    public byte[] getByteArray() {
        return buffer;
    }

    public int size() {
        return position;
    }

    @Override
    public String toString() {
        return String.format("[pos: %d, buffer-len: %d]", position, buffer.length);
    }

    @Override
    public void close() {
        //does nothing
    }

}
