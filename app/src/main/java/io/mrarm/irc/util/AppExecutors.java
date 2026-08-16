package io.mrarm.irc.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared executors for short-lived background work. */
public final class AppExecutors {

    public static final ExecutorService IO = Executors.newCachedThreadPool();

    private AppExecutors() {
    }
}
