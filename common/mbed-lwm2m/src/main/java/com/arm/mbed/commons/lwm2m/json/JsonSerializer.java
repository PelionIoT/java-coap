/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */

package com.arm.mbed.commons.lwm2m.json;

import com.arm.mbed.commons.lwm2m.LWM2MObjectInstance;
import com.arm.mbed.commons.lwm2m.LWM2MResource;
import com.arm.mbed.commons.lwm2m.LWM2MResourceInstance;
import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Serialises resources to JSON format according to the OMA LWM2M specification
 * chapter 6.3.4. OMA LWM2M resource model is described in chapter 6.1. 
 * @author nordav01
 */
public class JsonSerializer {
    
    private final Gson gson;
    
    /**
     * This method creates a new JSON serialiser.
     * @return New serialiser instance.
     */
    public static JsonSerializer create() {
        return new JsonSerializer();
    }
    
    private JsonSerializer() {
        gson = new GsonBuilder()
            .registerTypeAdapter(Number.class, new NumberTypeAdapter())
            .disableHtmlEscaping()
            .create();
    }
    
    public String serialize(JsonResourceArray array) {
        return gson.toJson(array);
    }
    
    /**
     * This method serialises the given resources including resource instances,
     * if there are any, and creates a JSON resource array as a <code>String</code>.
     * @param resources Array of resources and resource instances
     * @return JSON string containing a resource array
     */
    public String serialize(LWM2MResource... resources) {
        return serialize("", Arrays.asList(resources) );
    }
    
    /**
     * This method serialises the given list of resources including resource
     * instances, if there are any, and creates a JSON resource array as a 
     * <code>String</code>.
     * @param resources List of resources and resource instances
     * @return JSON string containing a resource array
     */
    public String serialize(List<LWM2MResource> resources) {
        return serialize("", resources);
    }

    /**
     * This method serialises all the resources of the given object instance 
     * including resource instances, if there are any, and creates a JSON 
     * resource array as a <code>String</code>. Note, that resulted JSON string
     * contains no information about the parent object instance at all.
     * 
     * @param object Object instance whose resources to be serialised
     * @return JSON string containing a resource array
     */
    public String serialize(LWM2MObjectInstance object) {
        String root = (object.getId().stringValue() == null ? "" : String.valueOf(object.getId())) + '/';
        return serialize(root, object.getResources() );
    }
    
    private String serialize(String root, List<LWM2MResource> resources) { // NOPMD This method is not unused!
        JsonResourceArray array = new JsonResourceArray();
        for (LWM2MResource resource: resources) {
            array.addResources(toJsonResource(root, resource) );
        }
        
        return serialize(array);
    }

    private List<JsonResource> toJsonResource(String root, LWM2MResource resource) {
        List<JsonResource> jsons = new ArrayList<>();
        
        if (resource.hasNestedInstances() ) {
            jsons.addAll(multipleResource(root, resource));
        } else {
            jsons.add (singleResource(root, resource));
        }
        
        return jsons;
    }
    
    private List<JsonResource> multipleResource(String root, LWM2MResource resource) {
        List<JsonResource> resources = new ArrayList<>();
        String rootName = root + resource.getId() + '/';
        
        for (LWM2MResourceInstance instance: resource.getNestedInstances()) {
            resources.add(createJsonResource(rootName + instance.getId(), instance));
        }
        
        return resources;
    }

    private JsonResource singleResource(String root, LWM2MResource resource) {
        return createJsonResource(root + resource.getId(), resource );
    }

    private JsonResource createJsonResource(String name, LWM2MResourceInstance resource) {
        JsonResource json;
        String stringValue = resource.getStringValue();
        
        if (resource.getType() != null) {
            switch (resource.getType()) {
                case INTEGER:
                    json = new JsonResource(name, Integer.parseInt(stringValue) );
                    break;
                    
                case FLOAT:
                    json = new JsonResource(name, Double.parseDouble(stringValue) );
                    break;
                    
                case BOOLEAN:
                    json = new JsonResource(name, resource.getValue()[0] == 0 ? false : true);
                    break;

                case OPAQUE:
                    json = new JsonResource(name, BaseEncoding.base64().encode(resource.getValue()) );
                    break;
                
                case STRING: 
                default: 
                    json = new JsonResource(name, stringValue);
            }
        } else {
            json = new JsonResource(name, stringValue);
        }
        
        return json;
    }
}