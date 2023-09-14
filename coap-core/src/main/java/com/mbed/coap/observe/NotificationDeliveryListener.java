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
package com.mbed.coap.observe;

import java.net.InetSocketAddress;

/**
 * Listener interface for notification delivery status.
 *
 * @author szymon
 */
public interface NotificationDeliveryListener {

    /**
     * Provides destination IP address of notification that was successfully
     * delivered.
     *
     * @param destinationAddress destination address
     */
    void onSuccess(InetSocketAddress destinationAddress);

    /**
     * Provides destination IP address of notification that failed to deliver.
     *
     * @param destinationAddress destination address
     */
    void onFail(InetSocketAddress destinationAddress);

    /**
     * Calls when there is no observer.
     */
    void onNoObservers();
}
