/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
 * Copyright (C) 2011-2021 ARM Limited. All rights reserved.
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
package com.mbed.coap.server;

import com.mbed.coap.packet.SeparateResponse;
import com.mbed.coap.utils.Filter;
import com.mbed.coap.utils.Service;
import java.util.concurrent.CompletableFuture;

class NotificationValidator implements Filter.SimpleFilter<SeparateResponse, Boolean> {

    @Override
    public CompletableFuture<Boolean> apply(SeparateResponse obs, Service<SeparateResponse, Boolean> service) {
        if (obs.options().getObserve() == null) {
            throw new IllegalArgumentException("Notification packet should have observation header set");
        }
        if (obs.getToken().isEmpty()) {
            throw new IllegalArgumentException("Notification packet should have non-empty token");
        }
        return service.apply(obs);
    }
}
