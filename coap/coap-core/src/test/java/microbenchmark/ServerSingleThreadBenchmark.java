/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package microbenchmark;

import java.util.concurrent.Executors;

/**
 * @author szymon
 */
public class ServerSingleThreadBenchmark extends ServerBenchmarkBase {

    public ServerSingleThreadBenchmark() {
        executor = Executors.newFixedThreadPool(1);
        trans = new FloodTransportStub(MAX);
    }

}
