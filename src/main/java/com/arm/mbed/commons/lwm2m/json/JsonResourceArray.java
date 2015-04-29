/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */

package com.arm.mbed.commons.lwm2m.json;

import com.google.common.collect.ImmutableList;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * This class represents a JSON resource array as described in OMA LWM2M
 * specification chapter 6.3.4.
 *
 * @author nordav01
 */
public class JsonResourceArray {

    public static final String CT_APPLICATION_LWM2M_JSON = "application/vnd.oma.lwm2m+json";

    @SerializedName("e")
    private final List<JsonResource> resources;
    @SerializedName("bt")
    private Integer baseTime;
    @SerializedName("bn")
    private String baseName;

    public JsonResourceArray() {
        resources = new ArrayList<>();
    }

    public List<JsonResource> getResources() {
        return ImmutableList.copyOf(resources);
    }

    public void addResources (List<JsonResource> resources) {
        this.resources.addAll(resources);
    }

    public Integer getBaseTime() {
        return baseTime;
    }

    public void setBaseTime(Integer baseTime) {
        this.baseTime = baseTime;
    }

    public String getBaseName() {
        return baseName;
    }

    public void setBaseName(String baseName) {
        this.baseName = baseName;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        for (JsonResource resource: resources) {
            b.append(resource).append(",\n");
        }
        if (baseTime != null) {
            b.append("bt:").append(baseTime).append(",\n");
        }
        if (baseName != null) {
            b.append("bn:").append("\"").append(baseName).append("\",\n");
        }
        return b.length()> 0 ? b.substring(0, b.length()-2) : "";
    }
}
