/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package microbenchmark;

import java.util.concurrent.Executors;

/**
 * @author szymon
 */
public class ServerSingleThreadBenchmark extends ServerBenchmarkBase {

    public ServerSingleThreadBenchmark() {
        executor = Executors.newFixedThreadPool(1);
        trans = new FloodTransportStub(MAX, executor);
    }

}
