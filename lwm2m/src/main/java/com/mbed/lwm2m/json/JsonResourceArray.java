/**
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

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Collections;
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
        return Collections.unmodifiableList(resources);
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
            b.append("bn:\"").append(baseName).append("\",\n");
        }
        return b.length()> 0 ? b.substring(0, b.length()-2) : "";
    }
}
