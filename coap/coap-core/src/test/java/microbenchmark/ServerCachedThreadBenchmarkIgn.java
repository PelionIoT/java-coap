/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package microbenchmark;

import java.util.concurrent.Executors;

/**
 * @author szymon
 */
public class ServerCachedThreadBenchmarkIgn extends ServerBenchmarkBase {

    public ServerCachedThreadBenchmarkIgn() {
        executor = Executors.newCachedThreadPool();
        trans = new FloodTransportStub(MAX, executor);
    }

    @Override
    public void coolDown() {
        super.coolDown();
        System.out.println(executor);
    }

}
