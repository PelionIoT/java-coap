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
package com.mbed.lwm2m;

import com.mbed.lwm2m.utils.HexArray;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LWM2MResource extends LWM2MResourceInstance {

    private List<LWM2MResourceInstance> instances;

    private LWM2MResource (LWM2MID id) {
        super (id);
    }

    public LWM2MResource (LWM2MID id, byte[] value) {
        super (id, value);
        this.validate();
    }

    public LWM2MResource (LWM2MID id, String value) {
        super (id, value);
        this.validate();
    }

    public LWM2MResource (LWM2MID id, int value) {
        super (id, value);
        this.validate();
    }

    public LWM2MResource (LWM2MID id, List<LWM2MResourceInstance> instances) {
        this (id);
        this.instances = new ArrayList<>(instances);
        this.validate();
    }

    public LWM2MResource (LWM2MID id, LWM2MResourceInstance... instances) {
        this (id, Arrays.asList(instances));
    }

    public final boolean hasNestedInstances() {
        return instances != null && !instances.isEmpty();
    }

    public List<LWM2MResourceInstance> getNestedInstances() {
        return instances;
    }

    public boolean addNestedInstance (LWM2MResourceInstance instance) {
        return instances.add(instance);
    }

    @Override
    public String toString() {
        return "Resource [id:" + getId() + ", value: " + (hasNestedInstances()
                ? instances.toString()
                : HexArray.toHex(getValue())) + "]";
    }

    private void validate() throws IllegalArgumentException {
        if (getId().intValue() < 0 || getId().intValue() > 65535) {
            throw new IllegalArgumentException("Resource ID must be between 0 and 65535.");
        }
        if (hasValue() == hasNestedInstances()) {
            throw new IllegalArgumentException("Resource must be a value or nested resource instances.");
        }
    }
}
