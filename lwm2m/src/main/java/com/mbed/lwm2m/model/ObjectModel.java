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
package com.mbed.lwm2m.model;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
        return Collections.unmodifiableList(resources);
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
