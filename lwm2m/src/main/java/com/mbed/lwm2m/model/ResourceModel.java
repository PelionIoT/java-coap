/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package com.mbed.lwm2m.model;

import com.google.gson.annotations.SerializedName;

/**
 * @author nordav01
 */
public class ResourceModel implements ResourceValidator {
    
    @SerializedName("resource-id")
    private final String resourceID;
    @SerializedName("resource-name")
    private final String resourceName;
    private final String operations;
    private final Instances instances;
    private final Type type;
    private final String range;
    private final String description;
    
    private ResourceValidator validator;
    
    public ResourceModel(
            String resourceID, String resourceName, String operations, 
            Instances instances, Type type, String range, String description) 
    {
        this.resourceID = resourceID;
        this.resourceName = resourceName;
        this.operations = operations;
        this.instances = instances;
        this.type = type;
        this.range = range;
        this.description = description;
    }

    public String getResourceID() {
        return resourceID;
    }

    public String getResourceName() {
        return resourceName;
    }

    public String getOperations() {
        return operations;
    }
    
    public boolean isReadable() {
        return operations.indexOf('R') != -1;
    }
    
    public boolean isWriteable() {
        return operations.indexOf('W') != -1;
    }
    
    public boolean isExecutable() {
        return operations.indexOf('E') != -1;
    }

    public Instances getInstances() {
        return instances;
    }

    public Type getType() {
        return type;
    }

    public String getRange() {
        return range;
    }

    public String getDescription() {
        return description;
    }
    
    @Override
    public boolean isValid (byte[] value) {
        return getValidator().isValid(value);    
    }
    
    @Override
    public boolean isValid (int value) {
        return getValidator().isValid(value);    
    }
    
    @Override
    public boolean isValid (String value) {
        return getValidator().isValid(value);
    }
    
    private synchronized ResourceValidator getValidator() {
        if (validator == null) {
            switch (type) {
                case STRING:
                    validator = StringResourceValidator.createResourceValidator(range);
                    break;
                    
                case INTEGER:
                    validator = IntegerResourceValidator.createResourceValidator(range);
                    break;
                
                case FLOAT:
                    break;
                
                case BOOLEAN:
                    validator = IntegerResourceValidator.createResourceValidator("[0,1]");
                    break;
                
                case OPAQUE:
                    validator = OpaqueResourceValidator.createResourceValidator(range);
                    break;
                
                case TIME:
                    validator = IntegerResourceValidator.createResourceValidator("[0-]");
                    break;
                    
                case EXECUTABLE:
                    validator = new DefaultResourceValidator(false);
                    break;
                    
                default:
            }
        }
        
        return validator;
    }

    @Override
    public String toString() {
        return "ResourceModel [resourceID=" + resourceID + ", resourceName="
                + resourceName + ", operations=" + operations + ", instances="
                + instances + ", type=" + type + ", range=" + range
                + ", description=" + description + "]";
    }
    
}
