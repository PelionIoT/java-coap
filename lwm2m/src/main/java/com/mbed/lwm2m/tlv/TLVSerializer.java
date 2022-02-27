/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
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
package com.mbed.lwm2m.tlv;

import static com.mbed.lwm2m.tlv.TLV.*;
import com.mbed.lwm2m.LWM2MObjectInstance;
import com.mbed.lwm2m.LWM2MResource;
import com.mbed.lwm2m.LWM2MResourceInstance;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

/**
 * TLV Serialiser constructs the binary representation of object instances,
 * resources and resource instances (see OMA-LWM2M specification, chapter 6.1
 * for resource model) as OMA-TLV according described in chapter 6.3.3. 
 */
public class TLVSerializer {

    /**
     * Serialises given objects instances that contain resources or multiple 
     * resources. Object instance IDs are also encoded. This method must be 
     * used when an operation targets an object with (potential) multiple 
     * instances like "GET /1". In that case the generated TLV will contain the
     * following data:
     * <ul>
     * <li> ./0
     * <li> ./0/0
     * <li> ./0/1
     * <li> ...
     * <li> ./1
     * <li> ./1/0
     * <li> ./1/1
     * <li> ...
     * </ul>
     *    
     * @param objects Array of object instances.
     * @return Object instances encoded binary as OMA-TLV 
     * @see #serializeObjectInstances(List) 
     */
    public static byte[] serialize (LWM2MObjectInstance... objects) {
        return serializeObjectInstances(Arrays.asList(objects) );
    }

    /**
     * Serialises given resources with no information about the parent object
     * instance. This method must be used when an operation targets an object
     * instance like "GET /1/0" or a single-instance object like "GET /3/0/".
     * Resources may have single or multiple instances. The generated TLV will 
     * contain the following data as response to "GET /3/0/":
     * <ul>
     * <li> ./0
     * <li> ./1
     * <li> ./2
     * <li> ./6/0 (1st instance of a multiple resource)
     * <li> ./6/1 (2nd instance of a multiple resource)
     * <li> ...
     * </ul>
     * @param resources Array of resources and resource instances.
     * @return Resources encoded binary as OMA-TLV
     * @see #serializeResources(List)
     */
    public static byte[] serialize (LWM2MResource... resources) {
        return serializeResources(Arrays.asList(resources));
    }

    /**
     * Serialises a list of objects instances that contain resources or multiple 
     * resources. Object instance IDs are also encoded. This method must be 
     * used when an operation targets an object with (potential) multiple 
     * instances like "GET /1". In that case the generated TLV will contain the
     * following data:
     * <ul>
     * <li> ./0
     * <li> ./0/0
     * <li> ./0/1
     * <li> ...
     * <li> ./1
     * <li> ./1/0
     * <li> ./1/1
     * <li> ...
     * </ul>
     *    
     * @param objects Array of object instances.
     * @return Object instances encoded binary as OMA-TLV 
     * @see #serializeObjectInstances(List) 
     */
    public static byte[] serializeObjectInstances (List<LWM2MObjectInstance> objects) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        
        for (int index=0; index<objects.size(); index++) {
            LWM2MObjectInstance object = objects.get(index);
            int id = object.getId().stringValue() == null ? index : object.getId().intValue();
            serialize(id, object, stream);
        }
        
        return stream.toByteArray();
    }

    /**
     * Serialises a list of resources with no information about the parent object
     * instance. This method must be used when an operation targets an object
     * instance like "GET /1/0" or a single-instance object like "GET /3/0/".
     * Resources may have single or multiple instances. The generated TLV will 
     * contain the following data as response to "GET /3/0/":
     * <ul>
     * <li> ./0
     * <li> ./1
     * <li> ./2
     * <li> ./6/0 (1st instance of a multiple resource)
     * <li> ./6/1 (2nd instance of a multiple resource)
     * <li> ...
     * </ul>
     * @param resources Array of resources and resource instances.
     * @return Resources encoded binary as OMA-TLV
     * @see #serializeResources(List)
     */
    public static byte[] serializeResources (List<LWM2MResource> resources) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        
        for (LWM2MResource resource: resources) {
            serialize(resource, stream);
        }
        
        return stream.toByteArray();
    }

    private static ByteArrayOutputStream serialize(int id, LWM2MObjectInstance object, ByteArrayOutputStream stream) {
        return serializeTILV(TYPE_OBJECT_INSTANCE, id, serializeResources(object.getResources() ), stream);
    }
    
    private static ByteArrayOutputStream serialize (LWM2MResource resource, ByteArrayOutputStream stream) {
        return resource.hasNestedInstances() 
                ? serializeMultipleResource(resource, stream) 
                : serializeResource(resource, stream);
    }

    private static ByteArrayOutputStream serializeResource(LWM2MResource resource, ByteArrayOutputStream stream) {
        return serializeTILV(TYPE_RESOURCE, resource.getId().intValue(), resource.getValue(), stream);
    }

    private static ByteArrayOutputStream serializeMultipleResource(LWM2MResource resource, ByteArrayOutputStream stream) {
        ByteArrayOutputStream nestedStream = new ByteArrayOutputStream();

        List<LWM2MResourceInstance> nestedInstances = resource.getNestedInstances();
        for (int index=0; index<nestedInstances.size(); index++) {
            LWM2MResourceInstance nested = nestedInstances.get(index);
            int id = nested.getId().stringValue() == null ? index : nested.getId().intValue();
            serializeResourceInstance(id, nested, nestedStream);
        }
        byte[] nestedValue = nestedStream.toByteArray();
        
        return serializeTILV(TYPE_MULTIPLE_RESOURCE, resource.getId().intValue(), nestedValue, stream);
    }

    private static ByteArrayOutputStream serializeResourceInstance(int id, LWM2MResourceInstance resource, ByteArrayOutputStream stream) {
        return serializeTILV(TYPE_RESOURCE_INSTANCE, id, resource.getValue(), stream);
    }
    
    private static ByteArrayOutputStream serializeTILV (byte type, int id, byte[] value, ByteArrayOutputStream stream) {
        int length = value.length;

        type += id < 256 ? 0 : ID16;
        type += length < 8 ? length : 
                length < 256 ? LENGTH8 : 
                length < 65536 ? LENGTH16 : LENGTH24;
        stream.write(type);
        
        serializeID(id, stream);
        serializeLength(length, stream);
        stream.write(value, 0, length);
        
        return stream;
    }

    private static void serializeID(int id, ByteArrayOutputStream stream) {
        if (id > 255) {
            stream.write( (id & 0xFF00) >> 8);
        }
        stream.write(id & 0xFF);
    }

    private static void serializeLength(int length, ByteArrayOutputStream stream) {
        if (length > 65535) {
            stream.write((length & 0xFF0000) >> 16);
            stream.write((length & 0xFF00) >> 8);
            stream.write(length & 0xFF);
        } else if (length > 255) {
            stream.write((length & 0xFF00) >> 8);
            stream.write(length & 0xFF);
        } else if (length > 7) {
            stream.write(length);
        }
    }
    
}
