/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package com.mbed.lwm2m;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LWM2MObjectInstance {

    private final LWM2MID id;
    private final List<LWM2MResource> resources;

    public LWM2MObjectInstance (LWM2MID id, List<LWM2MResource> resources) {
        if (id != null) {
            this.id = id;
            this.resources = new ArrayList<>(resources);
        } else {
            throw new NullPointerException("LWM2MID");
        }
    }

    public LWM2MObjectInstance (LWM2MID id, LWM2MResource... resources) {
        this (id, Arrays.asList(resources) );
    }

    public LWM2MObjectInstance (List<LWM2MResource> resources) {
        this (new LWM2MID(null), resources);
    }

    public LWM2MObjectInstance (LWM2MResource... resources) {
        this (Arrays.asList(resources) );
    }

    public LWM2MID getId() {
        return id;
    }

    public List<LWM2MResource> getResources() {
        return resources;
    }

    public LWM2MResource getResource(LWM2MID id) {
        for (LWM2MResource resource: resources) {
            if (resource.getId().equals(id) ) {
                return resource;
            }
        }
        return null;
    }

    public void addResource(LWM2MResource newResource) {
        resources.add(newResource);
    }

    public void addResources(List<LWM2MResource> newResources) {
        resources.addAll(newResources);
    }

    @Override
    public String toString() {
        return "ObjectInstance [id:" + id + ", value: " + resources.toString() + "]";
    }

}
