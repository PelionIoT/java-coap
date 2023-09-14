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
 * NULL implementation for interface NotificationDeliveryListener.
 *
 * @author szymon
 */
class NotificationDeliveryListenerNULL implements NotificationDeliveryListener {

    @Override
    public void onSuccess(InetSocketAddress destinationAddress) {
        //do nothing
    }

    @Override
    public void onFail(InetSocketAddress destinationAddress) {
        //do nothing
    }

    @Override
    public void onNoObservers() {
        //do nothing
    }
}
