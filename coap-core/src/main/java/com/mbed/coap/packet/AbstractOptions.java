/**
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mbed.coap.packet;

import com.mbed.coap.exception.CoapMessageFormatException;
import com.mbed.coap.exception.CoapUnknownOptionException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Common CoAP option properties.
 */
abstract class AbstractOptions {

    protected Map<Integer, RawOption> unrecognizedOptions;

    /**
     * Returns value for given un-recognize option number.
     *
     * @param optNumber option number
     * @return byte array value or null if does not exist
     */
    public byte[] getCustomOption(Integer optNumber) {
        if (!unrecognizedOptions.containsKey(optNumber)) {
            return null;
        }
        return unrecognizedOptions.get(optNumber).getFirstValue();
    }

    /**
     * Tests for unknown critical options. If any of critical option is unknown
     * then throws CoapUnknownOptionException
     *
     * @throws CoapUnknownOptionException when critical option is unknown
     */
    public void criticalOptTest() throws CoapUnknownOptionException {
        if (unrecognizedOptions == null) {
            return;
        }
        for (int tp : unrecognizedOptions.keySet()) {
            if (isCritical(tp)) {
                throw new CoapUnknownOptionException(tp);
            }
        }
    }

    public static boolean isCritical(int optionNumber) {
        return (optionNumber & 1) != 0;
    }

    public static boolean isUnsave(int optionNumber) {
        return (optionNumber & 2) != 0;
    }

    public static boolean hasNoCacheKey(int optionNumber) {
        return (optionNumber & 0x1e) == 0x1c;
    }

    protected boolean putUnrecognized(int optionNumber, byte[] data) {
        //unrecognized option header
        if (unrecognizedOptions == null) {
            unrecognizedOptions = new HashMap<>();
        }
        unrecognizedOptions.put(optionNumber, new RawOption(optionNumber, data));
        return true;
    }

    /**
     * Returns list with header options.
     *
     * @return sorted list
     */
    abstract List<RawOption> getRawOptions();

    /**
     * Returns number of header options. If greater that 14 then returns 15.
     *
     * @return option count
     */
    final byte getOptionCount() {
        int optCount = 0;
        for (RawOption rOpt : getRawOptions()) {
            optCount += rOpt.optValues.length == 0 ? 1 : rOpt.optValues.length;
        }
        return (byte) (optCount > 14 ? 15 : optCount);
    }

    public void serialize(OutputStream os) throws IOException {
        List<RawOption> list = getRawOptions();
        Collections.sort(list);

        int lastOptNumber = 0;
        for (RawOption opt : list) {
            for (byte[] optValue : opt.optValues) {
                int delta = opt.optNumber - lastOptNumber;
                lastOptNumber = opt.optNumber;
                if (delta > 0xFFFF + 269) {
                    throw new IllegalArgumentException("Delta with size: " + delta + " is not supported [option number: " + opt.optNumber + "]");
                }
                int len = optValue.length;
                if (len > 0xFFFF + 269) {
                    throw new IllegalArgumentException("Header size: " + len + " is not supported [option number: " + opt.optNumber + "]");
                }
                writeOptionHeader(delta, len, os);
                os.write(optValue);
            }
        }
    }

    static void writeOptionHeader(int delta, int len, OutputStream os) throws IOException {
        //first byte
        int tempByte;
        if (delta <= 12) {
            tempByte = delta << 4;
        } else if (delta < 269) {
            tempByte = 13 << 4;
        } else {
            tempByte = 14 << 4;
        }
        if (len <= 12) {
            tempByte |= len;
        } else if (len < 269) {
            tempByte |= 13;
        } else {
            tempByte |= 14;
        }
        os.write(tempByte);

        //extended option delta
        if (delta > 12 && delta < 269) {
            os.write(delta - 13);
        } else if (delta >= 269) {
            os.write((0xFF00 & (delta - 269)) >> 8);
            os.write(0x00FF & (delta - 269));
        }
        //extended len
        if (len > 12 && len < 269) {
            os.write(len - 13);
        } else if (len >= 269) {
            os.write((0xFF00 & (len - 269)) >> 8);
            os.write(0x00FF & (len - 269));
        }
    }

    protected OptionMeta getOptionMeta(InputStream is) throws IOException, CoapMessageFormatException {
        int hdrByte = is.read();
        if (hdrByte == CoapPacket.PAYLOAD_MARKER) {
            return null;
        }
        int delta = hdrByte >> 4;
        int len = 0xF & hdrByte;

        if (delta == 15 || len == 15) {
            throw new CoapMessageFormatException("Unexpected delta or len value in option header");
        }
        if (delta == 13) {
            delta += is.read();
        } else if (delta == 14) {
            delta = is.read() << 8;
            delta += is.read();
            delta += 269;
        }
        if (len == 13) {
            len += is.read();
        } else if (len == 14) {
            len = is.read() << 8;
            len += is.read();
            len += 269;
        }
        return new OptionMeta(delta, len);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        toString(sb);
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AbstractOptions that = (AbstractOptions) o;

        return unrecognizedOptions != null ? unrecognizedOptions.equals(that.unrecognizedOptions) : that.unrecognizedOptions == null;

    }

    @Override
    public int hashCode() {
        return unrecognizedOptions != null ? unrecognizedOptions.hashCode() : 0;
    }

    abstract void toString(StringBuilder sb);

    protected final class OptionMeta {
        final int delta;
        final int length;

        public OptionMeta(int delta, int length) {
            this.delta = delta;
            this.length = length;
        }
    }

}
