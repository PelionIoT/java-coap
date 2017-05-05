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
package com.mbed.lwm2m.model;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author nordav01
 */
public class ObjectRegistry {

    private static final String LWM2M_OBJECTS_JSON = "lwm2m-objects.json";

    @SerializedName("lwm2m-objects")
    private List<ObjectModel> objects;
    private Map<String, ObjectModel> objectMap;

    public static ObjectRegistry createObjectRegistry() {
        InputStream input = ObjectRegistry.class.getResourceAsStream(LWM2M_OBJECTS_JSON);
        Reader reader = new InputStreamReader(input);
        return createObjectRegistry(reader);
    }

    public static ObjectRegistry createObjectRegistry(Reader reader) {
        Gson gson = new Gson();
        return gson.fromJson(reader, ObjectRegistry.class).buildObjectMap();
    }

    private ObjectRegistry() {
        objectMap = new ConcurrentHashMap<>();
    }

    public ObjectRegistry(List<ObjectModel> objects) {
        this();
        this.objects = objects;
        buildObjectMap();
    }

    public void addObjectModels(List<ObjectModel> models) {
        for (ObjectModel object : models) {
            if (objectMap.put(object.getObjectID(), object) == null) {
                objects.add(object);
            } else {
                objects.set(indexOf(object), object);
            }
        }
    }

    public List<ObjectModel> getObjectModels() {
        return Collections.unmodifiableList(objects);
    }

    /**
     * Returns OMA resource type for given uri path. If could not find then
     * null.
     *
     * @param uriPath
     * @return OMA resource type
     * @throws InvalidResourceURIException
     */
    public Type getOmaResourceType(String uriPath) throws InvalidResourceURIException {
        try {
            String[] path = uriPath.split("/");
            if (path.length < 4) {
                throw new InvalidResourceURIException(uriPath + " does not match /{object-id}/{object-instance-id}/{resource-id}");
            }

            ObjectModel object = getObjectModel(path[1]);
            ResourceModel resource = object.getResourceModel(path[3]);

            return resource.getType();
        } catch (NotFoundException exception) {
            return null;
        }
    }

    public ObjectModel getObjectModel(String id) throws NotFoundException {
        if (id.charAt(0) == '/') {
            id = id.substring(1);
        }
        
        ObjectModel result = objectMap.get(id);
        if (result == null) {
            throw new NotFoundException(id);
        }

        return result;
    }

    @Override
    public String toString() {
        return "Model [objects=" + objects + "]";
    }

    private int indexOf(ObjectModel object) {
        for (int index = 0; index < objects.size(); index++) {
            if (objects.get(index).getObjectID().equals(object.getObjectID())) {
                return index;
            }
        }
        return -1;
    }

    private ObjectRegistry buildObjectMap() {
        for (ObjectModel object : objects) {
            objectMap.put(object.getObjectID(), object);
            object.buildResourceMap();
        }

        return this;
    }

}
