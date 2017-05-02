/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.utils;

import java.io.EOFException;
import java.io.InputStream;

/**
 * Non blocking byte array input stream.
 *
 * @author szymon
 */
public class ByteArrayBackedInputStream extends InputStream {

    protected byte[] buffer;
    protected int position;
    protected int mark;
    protected int count;

    public ByteArrayBackedInputStream(ByteArrayBackedOutputStream stream) {
        this.buffer = stream.buffer;
        this.position = 0;
        this.count = stream.position;
    }

    public ByteArrayBackedInputStream(byte buf[]) {
        this.buffer = buf;
        this.position = 0;
        this.count = buf.length;
    }

    public ByteArrayBackedInputStream(byte buf[], int offset, int length) {
        this.buffer = buf;
        this.position = offset;
        this.count = Math.min(offset + length, buf.length);
        this.mark = offset;
    }

    @Override
    public int read() throws EOFException {
        if (position < count) {
            return (buffer[position++] & 0xff);
        } else {
            throw new EOFException();
        }
    }

    @Override
    public int read(byte b[], int off, int len) throws EOFException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if (len <= 0) {
            return 0;
        }
        if (position >= count) {
            throw new EOFException();
        }
        if (position + len > count) {
            throw new EOFException();
        }
        System.arraycopy(buffer, position, b, off, len);
        position += len;
        return len;
    }

    @Override
    public long skip(long n) throws EOFException {
        if (position + n > count) {
            throw new EOFException();
        }
        if (n < 0) {
            return 0;
        }
        position += n;
        return n;
    }

    @Override
    public int available() {
        return count - position;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public synchronized void mark(int readAheadLimit) {
        mark = position;
    }

    @Override
    public synchronized void reset() {
        position = mark;
    }

    @Override
    public void close() {
        // nothing to do
    }
}
