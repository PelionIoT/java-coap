/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
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
package protocolTests.utils;

import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.packet.CoapResponse;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class ObservationListener implements Function<CoapResponse, Boolean> {

    BlockingQueue<CoapResponse> queue = new LinkedBlockingQueue<>();

    @Override
    public Boolean apply(CoapResponse obs) {

        queue.add(obs);
        return true;
    }

    public CoapResponse take() throws InterruptedException {
        return queue.poll(5, TimeUnit.SECONDS);
    }

    public void verifyReceived(CoapResponse obs) throws InterruptedException {
        CoapResponse received = queue.poll(1, TimeUnit.SECONDS);
        assertEquals(obs, received);
    }

    public boolean noMoreReceived() {
        return queue.isEmpty();
    }
}
