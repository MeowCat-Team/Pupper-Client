package cn.pupperclient.libraries.discordipc.impl;

import java.util.Random;

public class Backoff {
    private final long minAmount;
    private final long maxAmount;
    private final Random randGenerator;
    private long current;
    public Backoff(long min, long max) {
        this.minAmount = min;
        this.maxAmount = max;
        this.current = min;
        this.randGenerator = new Random();
    }

    public void reset() {
        current = minAmount;
    }

    public long nextDelay() {
        double delay = current * 2.0 * rand01();
        current = Math.min(current + (long) delay, maxAmount);
        return current;
    }

    private double rand01() {
        return randGenerator.nextDouble();
    }
}
