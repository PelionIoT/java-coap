/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.tlv;

import org.mbed.coap.CoapConstants;
import org.mbed.coap.utils.ByteArrayBackedInputStream;
import org.mbed.coap.utils.ByteArrayBackedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Implements TLV data structure.
 *
 * @author szymon
 */
public class TLVObject {

    private final short type;
    private final byte[] value;

    public TLVObject(short type, String strValue) {
        this(type, strValue.getBytes(CoapConstants.DEFAULT_CHARSET));
    }

    public TLVObject(short type, byte[] value) {
        if (value.length > 0xFFFF) {
            throw new IllegalArgumentException("To large value");
        }
        this.type = type;
        this.value = value;
    }

    /**
     * Returns object type.
     *
     * @return type
     */
    public short getType() {
        return type;
    }

    /**
     * Returns object value.
     *
     * @return value
     */
    public byte[] getValue() {
        return value;
    }

    public String getValueAsString() {
        return new String(value, CoapConstants.DEFAULT_CHARSET);
    }

    /**
     * Serialize into a stream.
     *
     * @param os output stream
     * @throws IOException
     */
    public void serialize(OutputStream os) throws IOException {
        //type
        os.write((type >> 8) & 0xFF);
        os.write(type & 0xFF);

        //size
        os.write((value.length >> 8) & 0xFF);
        os.write(value.length & 0xFF);

        //value
        os.write(value);
    }

    public static TLVObject deserialize(InputStream is) throws IOException {
        int type = 0;
        type = is.read() << 8;
        type |= is.read();

        int len = 0;
        len = is.read() << 8;
        len |= is.read();

        byte[] val = new byte[len];
        is.read(val);

        return new TLVObject((short) type, val);
    }

    public static TLVObject deserialize(byte[] data) throws IOException {
        ByteArrayBackedInputStream bais = new ByteArrayBackedInputStream(data);
        return deserialize(bais);
    }

    public byte[] serialize() throws IOException {
        ByteArrayBackedOutputStream baos = new ByteArrayBackedOutputStream();
        serialize(baos);
        return baos.toByteArray();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 17 * hash + this.type;
        hash = 17 * hash + Arrays.hashCode(this.value);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final TLVObject other = (TLVObject) obj;
        if (this.type != other.type) {
            return false;
        }
        if (!Arrays.equals(this.value, other.value)) {
            return false;
        }
        return true;
    }
}
