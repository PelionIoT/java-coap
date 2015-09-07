/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package microbenchmark;

import java.util.concurrent.Executors;

/**
 * @author szymon
 */
public class ServerCachedThreadBenchmarkIgn extends ServerBenchmarkBase {

    public ServerCachedThreadBenchmarkIgn() {
        executor = Executors.newCachedThreadPool();
        trans = new FloodTransportStub(MAX);
    }

    @Override
    public void coolDown() {
        super.coolDown();
        System.out.println(executor);
    }

}
