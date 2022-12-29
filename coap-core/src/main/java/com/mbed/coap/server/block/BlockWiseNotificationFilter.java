/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
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
package com.mbed.coap.server.block;

import com.mbed.coap.packet.Opaque;
import com.mbed.coap.packet.SeparateResponse;
import com.mbed.coap.server.messaging.Capabilities;
import com.mbed.coap.server.messaging.CapabilitiesResolver;
import com.mbed.coap.utils.Filter;
import com.mbed.coap.utils.Service;
import java.util.concurrent.CompletableFuture;

public class BlockWiseNotificationFilter implements Filter.SimpleFilter<SeparateResponse, Boolean> {
    private final CapabilitiesResolver capabilities;

    public BlockWiseNotificationFilter(CapabilitiesResolver capabilities) {
        this.capabilities = capabilities;
    }

    @Override
    public CompletableFuture<Boolean> apply(SeparateResponse blockObs, Service<SeparateResponse, Boolean> service) {
        SeparateResponse obs = blockObs;
        Capabilities csm = capabilities.getOrDefault(obs.getPeerAddress());
        if (csm.useBlockTransfer(obs.getPayload())) {
            //request that needs to use blocks
            obs = obs.duplicate();
            Opaque newPayload = BlockWiseTransfer.updateWithFirstBlock(obs, csm);
            obs = obs.payload(newPayload);
        }
        return service.apply(obs);
    }

}
