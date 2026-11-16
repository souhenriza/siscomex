package com.souhenriza.auth.session;

import java.time.Duration;

@FunctionalInterface
public interface Pause {
    void sleep(Duration duration) throws InterruptedException;

    static Pause thread() {
        return duration -> {
            long ms = (duration.toNanos() + 999_999) / 1_000_000;
            Thread.sleep(ms);
        };
    }
}
