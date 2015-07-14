/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package microbenchmark;

import java.util.concurrent.Executors;

/**
 * @author szymon
 */
public class ServerMultiThreadBenchmarkIgn extends ServerBenchmarkBase {

    public ServerMultiThreadBenchmarkIgn() {
        executor = Executors.newFixedThreadPool(8);
        trans = new FloodTransportStub(MAX);
    }
}
