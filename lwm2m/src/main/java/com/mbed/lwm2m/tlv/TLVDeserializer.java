/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
 * Copyright (c) 2023 Izuma Networks. All rights reserved.
 * 
 * SPDX-License-Identifier: Apache-2.0
 * 
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
package com.mbed.lwm2m.tlv;

import static com.mbed.lwm2m.tlv.TLV.*;
import com.mbed.lwm2m.LWM2MID;
import com.mbed.lwm2m.LWM2MObjectInstance;
import com.mbed.lwm2m.LWM2MResource;
import com.mbed.lwm2m.LWM2MResourceInstance;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * TLV Deserialiser get the object instances and resources as binary data and
 * builds the <code>lwm2m</code> representation from it. See OMA-LWM2M
 * specification, chapter 6.1 for the resource model and chapter 6.3.3 for
 * the OMA-TLV specification.
 *
 * @author nordav01
 */
public class TLVDeserializer {

    private static class TypeIdLength {
        private byte[] tlv;
        private int offset;
        private int type;
        private int id;
        private int length;

        private static TypeIdLength createTypeIdLength(byte[] tlv, int offset) {
            TypeIdLength til = new TypeIdLength();
            til.tlv = tlv;
            til.offset = offset;
            til.type = tlv[offset] & (byte) 0b11_000000;
            til.id = -1;
            til.length = 0;
            return til;
        }

        private TypeIdLength deserialize() {
            try {
                int idLength = tlv[offset] & ID16;
                int lengthType = tlv[offset] & LENGTH24;
                if (lengthType == 0) {
                    length = tlv[offset] & (byte) 0b00000_111;
                }
                offset++;

                deserialiseID(idLength);
                deserialiseLength(lengthType);
            } catch (IndexOutOfBoundsException exception) {
                throw new IllegalArgumentException("Premature end of content...", exception);
            }

//            if (length == 0) {
//                throw new IllegalArgumentException("Length of value cannot be zero.");
//            }

            return this;
        }

        private void deserialiseID (int idLength) {
            id = tlv[offset++] & 0xFF;
            if (idLength == ID16) {
                id = (id << 8) + (tlv[offset++] & 0xFF);
            }
        }

        private void deserialiseLength (int lengthType) {
            if (lengthType > 0) {
                length = tlv[offset++] & 0xFF;
            }
            if (lengthType > LENGTH8) {
                length = (length << 8) + (tlv[offset++] & 0xFF);
            }
            if (lengthType > LENGTH16) {
                length = (length << 8) + (tlv[offset++] & 0xFF);
            }
        }

    }

    /**
     * This method checks whether the given binary encodes an object instance
     * or something else. It returns <code>true</code> if bits 7-6 of the first
     * byte is "00".
     * @param tlv Binary to be checked as LWM2M object instance
     * @return <code>true</code> or <code>false</code>.
     */
    public static boolean isObjectInstance (byte[] tlv) {
        return isObjectInstance(tlv, 0);
    }

    /**
     * This method checks whether the given binary encodes a resource or
     * something else. It returns <code>true</code> if bits 7-6 of the first
     * byte is "11".
     * @param tlv Binary to be checked as LWM2M resource.
     * @return <code>true</code> or <code>false</code>.
     */
    public static boolean isResource (byte[] tlv) {
        return isResource(tlv, 0);
    }

    /**
     * This method checks whether the given binary encodes a multiple resource
     * or something else. It returns <code>true</code> if bits 7-6 of the first
     * byte is "10".
     * @param tlv Binary to be checked as LWM2M multiple resource.
     * @return <code>true</code> or <code>false</code>.
     */
    public static boolean isMultipleResource (byte[] tlv) {
        return isMultipleResource(tlv, 0);
    }

    /**
     * This method checks whether the given binary encodes a resource instance
     * or something else. It returns <code>true</code> if bits 7-6 of the first
     * byte is "01".
     * @param tlv Binary to be checked as LWM2M resource instance.
     * @return <code>true</code> or <code>false</code>.
     */
    public static boolean isResourceInstance (byte[] tlv) {
        return isResourceInstance(tlv, 0);
    }

    /**
     * Deserialises the given binary that must encode object instances. Binary
     * array can be checked before invoking this method with
     * {@link #isObjectInstance(byte[])}.
     * @param tlv Binary in OMA-TLV format
     * @return List of <code>LWM2MObjectInstance</code> objects.
     * @throws IllegalArgumentException if given binary is not a valid OMA-TLV
     *         or it encodes a structure other than object instances.
     * @see #deserializeResources(byte[])
     */
    public static List<LWM2MObjectInstance> deserialiseObjectInstances (byte[] tlv) {
        if (!isObjectInstance(tlv) ) {
            throw new IllegalArgumentException("Object instance not found.");
        }

        return deserializeObjectInstances (tlv, 0, new ArrayList<LWM2MObjectInstance>() );
    }

    /**
     * Deserialises the given binary that must encode resources. Binary array
     * can be checked before invoking this method with {@link #isResource(byte[])}.
     * @param tlv Binary in OMA-TLV format
     * @return List of <code>LWM2MObjectInstance</code> objects.
     * @throws IllegalArgumentException if given binary is not a valid OMA-TLV
     *         or it encodes a structure other than object instances.
     * @see #deserializeResources(byte[])
     */
    public static List<LWM2MResource> deserializeResources (byte[] tlv) {
        if (!isResource(tlv) && !isMultipleResource(tlv)) {
            throw new IllegalArgumentException("Resource or multiple resource not found.");
        }

        return deserializeResources (tlv, 0, new ArrayList<LWM2MResource>() );
    }

    private static List<LWM2MObjectInstance> deserializeObjectInstances(byte[] tlv, int offset, List<LWM2MObjectInstance> list) {
        TypeIdLength til = TypeIdLength.createTypeIdLength(tlv, offset).deserialize();
        offset = til.offset;

        if (til.type == TYPE_OBJECT_INSTANCE) {
            List<LWM2MResource> instances = new ArrayList<>();
            byte[] nested = Arrays.copyOfRange(tlv, offset, offset + til.length);
            deserializeResources(nested, 0, instances);
            list.add (new LWM2MObjectInstance(LWM2MID.from(til.id), instances));
        } else {
            throw new IllegalArgumentException("Object instance is expected at index:" + offset);
        }

        offset += til.length;

        return offset < tlv.length ? deserializeObjectInstances(tlv, offset, list) : list;
    }

    private static List<LWM2MResource> deserializeResources (byte[] tlv, int offset, List<LWM2MResource> list) {
        TypeIdLength til = TypeIdLength.createTypeIdLength(tlv, offset).deserialize();
        offset = til.offset;

        if (til.type == TYPE_RESOURCE || til.type == TYPE_RESOURCE_INSTANCE) {
            byte[] value = til.length > 0 ? Arrays.copyOfRange(tlv, offset, offset + til.length) : new byte[0];
            list.add (new LWM2MResource(LWM2MID.from(til.id), value));
        } else if (til.type == TYPE_MULTIPLE_RESOURCE) {
            List<LWM2MResourceInstance> instances = new ArrayList<>();
            byte[] nested = Arrays.copyOfRange(tlv, offset, offset + til.length);
            deserializeResourceInstances (nested, 0, instances);
            list.add (new LWM2MResource(LWM2MID.from(til.id), instances));
        } else {
            throw new IllegalArgumentException("Resource is expected at index:" + offset);
        }

        offset += til.length;

        return offset < tlv.length ? deserializeResources(tlv, offset, list) : list;
    }

    private static List<LWM2MResourceInstance> deserializeResourceInstances (byte[] tlv, int offset, List<LWM2MResourceInstance> list) {
        TypeIdLength til = TypeIdLength.createTypeIdLength(tlv, offset).deserialize();
        offset = til.offset;

        if (til.type == TYPE_RESOURCE_INSTANCE) {
            byte[] value = til.length > 0 ? Arrays.copyOfRange(tlv, offset, offset + til.length) : new byte[0];
            list.add (new LWM2MResourceInstance(LWM2MID.from(til.id), value));
        } else {
            throw new IllegalArgumentException("Resource instance is expected at index:" + offset);
        }

        offset += til.length;

        return offset < tlv.length ? deserializeResourceInstances(tlv, offset, list) : list;
    }

    private static boolean isObjectInstance (byte[] tlv, int offset) {
        return (tlv[offset] & (byte) 0b11_000000) == TYPE_OBJECT_INSTANCE;
    }

    private static boolean isResource (byte[] tlv, int offset) {
        return (tlv[offset] & (byte) 0b11_000000) == TYPE_RESOURCE;
    }

    private static boolean isMultipleResource (byte[] tlv, int offset) {
        return (tlv[offset] & (byte) 0b11_000000) == TYPE_MULTIPLE_RESOURCE;
    }

    private static boolean isResourceInstance (byte[] tlv, int offset) {
        return (tlv[offset] & (byte) 0b11_000000) == TYPE_RESOURCE_INSTANCE;
    }

}
