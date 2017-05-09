/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */

package com.mbed.lwm2m.json;

import com.google.gson.annotations.SerializedName;
import com.mbed.lwm2m.LWM2MResourceType;

/**
 * @author nordav01
 */
public class JsonResource {

    @SerializedName("n")
    private final String name;

    @SerializedName("sv")
    private String stringValue;

    @SerializedName("v")
    private Number numericalValue;

    @SerializedName("bv")
    private Boolean booleanValue;

    @SerializedName("t")
    private Integer time;

    @SerializedName("ov")
    private String objectLinkValue;

    private JsonResource (String name) {
        this.name = name;
    }

    public JsonResource (String name, String stringValue) {
        this(name);
        this.stringValue = stringValue;
    }

    public JsonResource (String name, Number numericalValue) {
        this(name);
        this.numericalValue = numericalValue;
    }

    public JsonResource (String name, Boolean booleanValue) {
        this(name);
        this.booleanValue = booleanValue;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        if (stringValue != null) {
            return stringValue;
        } else if (numericalValue != null) {
            return numericalValue.toString();
        } else if (booleanValue != null) {
            return booleanValue ? "1" : "0";
        }
        return null;
    }

    public String getStringValue() {
        return stringValue;
    }

    public Number getNumericalValue() {
        return numericalValue;
    }

    public Boolean getBooleanValue() {
        return booleanValue;
    }

    public String getObjectLinkValue() {
        return objectLinkValue;
    }

    public Integer getTime() {
        return time;
    }

    public void setTime(Integer time) {
        this.time = time;
    }

    public LWM2MResourceType getType() {
        if (stringValue != null) {
            return LWM2MResourceType.STRING;
        } else if (numericalValue != null) {
            return numericalValue instanceof Byte ||
                   numericalValue instanceof Short ||
                   numericalValue instanceof Integer ||
                   numericalValue instanceof Long ? LWM2MResourceType.INTEGER : LWM2MResourceType.FLOAT;
        } else if (booleanValue != null) {
            return LWM2MResourceType.BOOLEAN;
        } else if (objectLinkValue != null) {
            return LWM2MResourceType.OBJECTLINK;
        }
        return null;
    }

}
