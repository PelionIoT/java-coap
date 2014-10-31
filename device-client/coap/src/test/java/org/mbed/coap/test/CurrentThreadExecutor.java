/*
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.test;

import java.util.concurrent.Executor;

/**
 * Created by szymon
 */
public class CurrentThreadExecutor implements Executor {
    @Override
    public void execute(Runnable command) {
        command.run();
    }
}
