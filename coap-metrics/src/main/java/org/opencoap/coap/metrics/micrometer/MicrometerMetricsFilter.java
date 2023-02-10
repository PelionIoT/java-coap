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

import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.utils.Filter;
import com.mbed.coap.utils.Service;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.logging.LoggingMeterRegistry;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MicrometerMetricsFilter implements Filter.SimpleFilter<CoapRequest, CoapResponse> {
    private final MeterRegistry registry;
    private final String metricName;

    public static MicrometerMetricsFilterBuilder builder() {
        return new MicrometerMetricsFilterBuilder();
    }

    MicrometerMetricsFilter(MeterRegistry registry, String metricName, DistributionStatisticConfig distributionStatisticConfig) {
        this.registry = registry;
        this.metricName = metricName;

        registry.config().meterFilter(new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                if (id.getName().equals(metricName)) {
                    return distributionStatisticConfig.merge(config);
                }

                return config;
            }
        });
    }

    @Override
    public CompletableFuture<CoapResponse> apply(CoapRequest req, Service<CoapRequest, CoapResponse> service) {
        Timer.Sample timer = Timer.start();

        return service.apply(req).whenComplete((resp, err) -> {
            Timer metric = registry.timer(metricName, requestTags(req, resp, err));
            timer.stop(metric);
        });
    }

    private List<Tag> requestTags(CoapRequest req, CoapResponse resp, Throwable err) {
        String uriPath = req.options().getUriPath();
        return Arrays.asList(
                Tag.of("method", req.getMethod().name()),
                Tag.of("status", resp != null ? resp.getCode().codeToString() : "n/a"),
                Tag.of("route", uriPath != null ? uriPath : "/"),
                Tag.of("throwable", err != null ? err.getClass().getCanonicalName() : "n/a")
        );
    }

    public static class MicrometerMetricsFilterBuilder {
        public String DEFAULT_METRIC_NAME = "coap.server.requests";
        private MeterRegistry registry;
        private String metricName;
        private DistributionStatisticConfig distributionStatisticConfig;

        MicrometerMetricsFilterBuilder() {
        }

        public MicrometerMetricsFilterBuilder registry(MeterRegistry registry) {
            this.registry = registry;
            return this;
        }

        public MicrometerMetricsFilterBuilder metricName(String metricName) {
            this.metricName = metricName;
            return this;
        }

        public MicrometerMetricsFilterBuilder distributionStatisticConfig(DistributionStatisticConfig distributionStatisticConfig) {
            this.distributionStatisticConfig = distributionStatisticConfig;
            return this;
        }

        public MicrometerMetricsFilter build() {
            if (this.registry == null) {
                this.registry = new LoggingMeterRegistry();
            }

            if (this.metricName == null) {
                this.metricName = DEFAULT_METRIC_NAME;
            }

            if (this.distributionStatisticConfig == null) {
                this.distributionStatisticConfig = DistributionStatisticConfig.builder().percentiles(0.5, 0.9, 0.95, 0.99).build();
            }

            return new MicrometerMetricsFilter(this.registry, this.metricName, this.distributionStatisticConfig);
        }
    }
}
