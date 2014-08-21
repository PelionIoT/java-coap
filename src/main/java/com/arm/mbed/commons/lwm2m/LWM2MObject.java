/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package com.arm.mbed.commons.lwm2m;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LWM2MObject {

    private final LWM2MID id;
    private final List<LWM2MObjectInstance> instances;

    public LWM2MObject (LWM2MID id, List<LWM2MObjectInstance> instances) {
        if (id != null) {
            this.id = id;
            this.instances = new ArrayList<>(instances);
        } else {
            throw new NullPointerException("LWM2MID");
        }
    }

    public LWM2MObject (LWM2MID id, LWM2MObjectInstance... instances) {
        this (id, Arrays.asList(instances) );
    }

    public LWM2MObject (List<LWM2MObjectInstance> instances) {
        this (new LWM2MID(null), instances);
    }

    public LWM2MObject (LWM2MObjectInstance... instances) {
        this (Arrays.asList(instances) );
    }

    public LWM2MID getId() {
        return id;
    }

    public List<LWM2MObjectInstance> getInstances() {
        return instances;
    }

    public LWM2MObjectInstance getInstance(LWM2MID instanceId) {
        for (LWM2MObjectInstance instance: instances) {
            if (instance.getId().equals(instanceId) ) {
                return instance;
            }
        }
        return null;
    }

    public void addInstance(LWM2MObjectInstance newInstance) {
        instances.add(newInstance);
    }

    public void addInstances(List<LWM2MObjectInstance> newInstances) {
        instances.addAll(newInstances);
    }

    @Override
    public String toString() {
        return "LWM2MObject [id:" + id + ", instances: " + instances.toString() + "]";
    }

}
