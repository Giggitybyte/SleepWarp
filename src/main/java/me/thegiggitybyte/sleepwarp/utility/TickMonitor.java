package me.thegiggitybyte.sleepwarp.utility;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps a rolling average of ticks per second by measuring the amount of time that passes every 20 ticks.
 */
public class TickMonitor {
    private static class Measurement {
        final long timeNanoseconds;
        final BigDecimal totalTickCount;
        final BigDecimal skippedTickCount;
        
        Measurement(long timeNanoseconds, BigDecimal successfulTicks, BigDecimal skippedTicks) {
            this.timeNanoseconds = timeNanoseconds;
            this.totalTickCount = successfulTicks;
            this.skippedTickCount = skippedTicks;
        }
    }
    
    private static final BigDecimal TWENTY_BILLION_NANOSECONDS = BigDecimal.valueOf(TimeUnit.SECONDS.toNanos(20));
    private static final BigDecimal ONE_BILLION_NANOSECONDS = BigDecimal.valueOf(TimeUnit.SECONDS.toNanos(1));
    private static final BigDecimal TWENTY = BigDecimal.valueOf(20);
    private static final int MAX_MEASUREMENT_COUNT = 15;
    
    private static TickMonitor INSTANCE = null;
    
    private BigDecimal averageTickRate, averageTickLoss;
    private AtomicLong currentTick;
    private ArrayList<Measurement> measurements;
    
    public static void initialize() {
        if (INSTANCE != null) throw new AssertionError("Tick monitor already initialized.");
        
        INSTANCE = new TickMonitor();
        INSTANCE.currentTick = new AtomicLong();
        INSTANCE.measurements = new ArrayList<>(MAX_MEASUREMENT_COUNT);
        INSTANCE.averageTickRate = TWENTY;
        INSTANCE.averageTickLoss = BigDecimal.ZERO;
        
        ServerTickEvents.START_SERVER_TICK.register(INSTANCE::onStartTick);
    }
    
    public static TickMonitor getInstance() {
        if (INSTANCE == null) throw new AssertionError("Tick monitor not initialized.");
        return INSTANCE;
    }
    
    private void onStartTick(MinecraftServer server) {
        if (currentTick.getAndIncrement() % 20 != 0) return;
        
        long currentTimeNanoseconds = System.nanoTime();
        long lastMeasurementNanoseconds = (measurements.size() == 0)
                ? BigDecimal.valueOf(currentTimeNanoseconds).subtract(ONE_BILLION_NANOSECONDS).longValue()
                : measurements.get(measurements.size() - 1).timeNanoseconds;
        
        var processingTimeNanoseconds = BigDecimal.valueOf(currentTimeNanoseconds - lastMeasurementNanoseconds);
        var ticksPerSecond = TWENTY_BILLION_NANOSECONDS.divide(processingTimeNanoseconds, 30, RoundingMode.HALF_EVEN);
        var skippedTicksCount = ticksPerSecond.compareTo(TWENTY) < 0 ? TWENTY.subtract(ticksPerSecond) : BigDecimal.ZERO;
        
        if (measurements.size() == MAX_MEASUREMENT_COUNT) measurements.remove(0);
        measurements.add(new Measurement(currentTimeNanoseconds, ticksPerSecond, skippedTicksCount));
        
        var totalTicks = BigDecimal.ZERO;
        var totalLoss = BigDecimal.ZERO;
        for (var measurement : measurements) {
            totalTicks = totalTicks.add(measurement.totalTickCount);
            totalLoss = totalLoss.add(measurement.skippedTickCount);
        }
        
        var sampleSize = BigDecimal.valueOf(measurements.size());
        averageTickRate = totalTicks.divide(sampleSize, RoundingMode.HALF_EVEN);
        averageTickLoss = totalLoss.divide(sampleSize, RoundingMode.HALF_EVEN);
    }
    
    public int getAverageTickRate() {
        return averageTickRate.setScale(0, RoundingMode.HALF_UP).intValue();
    }
    
    public int getAverageTickLoss() {
        return averageTickLoss.setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
