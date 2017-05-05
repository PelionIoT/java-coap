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
package com.mbed.lwm2m;

import com.mbed.lwm2m.utils.HexArray;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

public class LWM2MResourceInstance {

    private static Charset defaultCharset = Charset.defaultCharset();

    private final LWM2MID id;
    private byte[] value;
    private LWM2MResourceType type;
    private LWM2MResourceType repType;

    protected LWM2MResourceInstance(LWM2MID id) {
        if (id != null) {
            this.id = id;
        } else {
            throw new NullPointerException("LWM2MID");
        }
    }

    public LWM2MResourceInstance(LWM2MID id, byte[] value) {
        this(id);
        this.value = value;

        if (value == null) {
            throw new IllegalArgumentException("Missing value from resource instance.");
        }
        this.repType = LWM2MResourceType.OPAQUE;
    }

    public LWM2MResourceInstance(LWM2MID id, String value) {
        this(id, value.getBytes());
        this.repType = LWM2MResourceType.STRING;
    }

    public LWM2MResourceInstance(LWM2MID id, int value) {
        this(id);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        if ((value & 0xFF000000) != 0) {
            stream.write((value & 0xFF000000) >> 24);
        }
        if ((value & 0xFFFF0000) != 0) {
            stream.write((value & 0x00FF0000) >> 16);
        }
        if ((value & 0xFFFFFF00) != 0) {
            stream.write((value & 0x0000FF00) >> 8);
        }
        stream.write(value & 0x000000FF);

        this.value = stream.toByteArray();
        this.repType = LWM2MResourceType.INTEGER;
    }

    public LWM2MID getId() {
        return id;
    }

    public byte[] getValue() {
        return value;
    }

    public String getStringValue() {
        if (repType == LWM2MResourceType.INTEGER) {
            long longValue = 0L;
            for (int i = 0; i < value.length; i++) {
                longValue = (longValue << 8) + (value[i] & 0xFF);
            }
            return String.valueOf(longValue);
        } else {
            return new String(value, defaultCharset);
        }
    }

    public boolean hasValue() {
        return value != null;
    }

    public LWM2MResourceType getType() {
        return type != null ? type : repType;
    }

    public void setType(LWM2MResourceType type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return "Resource instance [id:" + id + ", value: " + HexArray.toHex(value) + "]";
    }

}
