/*
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
package com.mbed.lwm2m.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mbed.lwm2m.LWM2MID;
import com.mbed.lwm2m.LWM2MResource;
import com.mbed.lwm2m.LWM2MResourceInstance;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deserialises resources from the JSON representation according to the OMA LWM2M specification chapter 6.3.4. OMA LWM2M
 * resource model is described in chapter 6.1. Historical representation of a resource will be created as individual
 * resources with the same resource ID.
 *
 * @author nordav01
 */
public class JsonDeserializer {

    private final Gson gson;

    /**
     * Creates a new Deserialiser instance.
     *
     * @return
     */
    public static JsonDeserializer create() {
        return new JsonDeserializer();
    }

    private JsonDeserializer() {
        gson = new GsonBuilder()
                .registerTypeAdapter(Number.class, new NumberTypeAdapter())
                .disableHtmlEscaping()
                .create();
    }

    /**
     * Parses and deserialises the given JSON string and returns it as a {@link JsonResourceArray}.
     *
     * @param json JSON string to be deserialised.
     * @return A <code>JsonResourceArray</code> instance.
     */
    public JsonResourceArray asJsonResourceArray(String json) {
        return gson.fromJson(json, JsonResourceArray.class);
    }

    /**
     * Parses and deserialises the given JSON string and returns it as a list of LWM2M resources.
     *
     * @param json JSON string to be deserialised.
     * @return A list of resources.
     */
    public List<LWM2MResource> deserialize(String json) {
        JsonResourceArray array = asJsonResourceArray(json);
        List<LWM2MResource> resources = new ArrayList<>();
        Map<String, LWM2MResource> multipleResources = new HashMap<>();

        for (JsonResource jsonResource : array.getResources()) {
            LWM2MResource lwm2mResource = toLWM2MResource(jsonResource, multipleResources);
            if (lwm2mResource != null) {
                resources.add(lwm2mResource);
            }
        }

        return resources;
    }

    private static LWM2MResource toLWM2MResource(JsonResource jsonResource, Map<String, LWM2MResource> multipleResources) {
        LWM2MResource resource;
        String[] name = jsonResource.getName().split("/");
        if (name.length == 1) {
            resource = parseResource(name[0], jsonResource);
        } else {
            LWM2MResourceInstance instance = parseResourceInstance(name[1], jsonResource);
            resource = resolveResource(name[0], instance, multipleResources);
        }

        return resource;
    }

    private static LWM2MResource parseResource(String id, JsonResource jsonResource) {
        LWM2MResource resource;

        switch (jsonResource.getType()) {
            case STRING:
                resource = new LWM2MResource(LWM2MID.from(id), jsonResource.getStringValue());
                break;

            case INTEGER:
            case FLOAT:
                resource = new LWM2MResource(LWM2MID.from(id), String.valueOf(jsonResource.getNumericalValue()));
                break;

            case BOOLEAN:
                resource = new LWM2MResource(LWM2MID.from(id), jsonResource.getBooleanValue() ? 1 : 0);
                break;

            default:
                throw new IllegalArgumentException("Invalid json resource");
        }

        resource.setType(jsonResource.getType());
        return resource;
    }

    private static LWM2MResourceInstance parseResourceInstance(String id, JsonResource jsonResource) {
        LWM2MResourceInstance instance;

        switch (jsonResource.getType()) {
            case STRING:
                instance = new LWM2MResourceInstance(LWM2MID.from(id), jsonResource.getStringValue());
                break;

            case INTEGER:
            case FLOAT:
                instance = new LWM2MResourceInstance(LWM2MID.from(id), String.valueOf(jsonResource.getNumericalValue()));
                break;

            case BOOLEAN:
                instance = new LWM2MResourceInstance(LWM2MID.from(id), jsonResource.getBooleanValue() ? 1 : 0);
                break;

            default:
                throw new IllegalArgumentException("Invalid json resource");
        }

        instance.setType(jsonResource.getType());
        return instance;
    }

    private static LWM2MResource resolveResource(String id, LWM2MResourceInstance instance, Map<String, LWM2MResource> multipleResources) {
        LWM2MResource resource;
        LWM2MResource multipleResource = multipleResources.get(id);
        if (multipleResource == null) {
            resource = new LWM2MResource(LWM2MID.from(id), instance);
            multipleResources.put(id, resource);
        } else {
            multipleResource.addNestedInstance(instance);
            resource = null;
        }

        return resource;
    }

}
