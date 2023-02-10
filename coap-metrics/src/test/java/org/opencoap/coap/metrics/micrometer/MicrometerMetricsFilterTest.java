/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
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
package org.opencoap.coap.metrics.micrometer;

import static com.mbed.coap.packet.CoapRequest.get;
import static com.mbed.coap.packet.CoapResponse.ok;
import static com.mbed.coap.utils.FutureHelpers.failedFuture;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.utils.Service;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.logging.LoggingMeterRegistry;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class MicrometerMetricsFilterTest {
    private final MeterRegistry registry = new LoggingMeterRegistry();
    private final MicrometerMetricsFilter filter = MicrometerMetricsFilter.builder().registry(registry).build();
    private final Service<CoapRequest, CoapResponse> okService = filter.then(__ -> completedFuture(ok("OK")));
    private final Service<CoapRequest, CoapResponse> failingService = filter.then(__ -> failedFuture(new Exception("error message")));

    @Test
    public void shouldBuildFilter() {
        MicrometerMetricsFilter.builder()
                .metricName("test")
                .registry(registry)
                .distributionStatisticConfig(DistributionStatisticConfig.builder().percentiles(0.5, 0.95).build())
                .build();

        MicrometerMetricsFilter.builder().build();
    }

    @Test
    public void shouldForwardResponse() throws ExecutionException, InterruptedException {
        CoapResponse resp = okService.apply(get("/test/1")).get();

        assertEquals(resp, ok("OK"));
    }

    @Test
    public void shouldRegisterTimerMetric() {
        okService.apply(get("/test/1")).join();
        assertNotNull(
                registry.find("coap.server.requests")
                        .tag("route", "/test/1")
                        .tag("status", "205")
                        .tag("method", "GET")
                        .tag("throwable", "n/a")
                        .timer()
        );

        assertThrows(Exception.class, () -> failingService.apply(get("/test/2")).get());
        assertNotNull(
                registry.find("coap.server.requests")
                        .tag("route", "/test/2")
                        .tag("status", "n/a")
                        .tag("method", "GET")
                        .tag("throwable", "java.lang.Exception")
                        .timer()
        );

        okService.apply(get("/")).join();
        assertNotNull(
                registry.find("coap.server.requests")
                        .tag("route", "/")
                        .tag("status", "205")
                        .tag("method", "GET")
                        .tag("throwable", "n/a")
                        .timer()
        );
    }
}
