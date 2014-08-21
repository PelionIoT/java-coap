/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package com.arm.mbed.commons.lwm2m.model;

import com.google.common.collect.ImmutableList;
import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author nordav01
 */
public class ObjectModel {

    @SerializedName("object-id")
    private String objectID;
    @SerializedName("object-name")
    private String objectName;
    private String description;
    private Instances instances;
    private List<ResourceModel> resources;
    private Map<String, ResourceModel> resourceMap;
    
    private ObjectModel() {
        resourceMap = new ConcurrentHashMap<>();
    }
    
    public ObjectModel(
            String objectID, String objectName, String description,
            Instances instances, List<ResourceModel> resources) 
    {
        this();
        this.objectID = objectID;
        this.objectName = objectName;
        this.description = description;
        this.instances = instances;
        this.resources = resources;
        buildResourceMap();
    }

    public String getObjectID() {
        return objectID;
    }

    public String getObjectName() {
        return objectName;
    }

    public String getDescription() {
        return description;
    }

    public Instances getInstances() {
        return instances;
    }

    public List<ResourceModel> getResources() {
        return ImmutableList.copyOf(resources);
    }
    
    public ResourceModel getResourceModel (String id) throws NotFoundException {
        ResourceModel result = resourceMap.get(id);
        if (result == null) {
            throw new NotFoundException(id);
        }
        
        return result;
    }

    @Override
    public String toString() {
        return "ObjectModel [objectID=" + objectID + ", objectName="
                + objectName + ", description=" + description + ", instances="
                + instances + ", resources=" + resources + "]";
    }
    
    final void buildResourceMap() {
        for (ResourceModel resource: resources) {
            resourceMap.put(resource.getResourceID(), resource);
        }
    }
    
}
