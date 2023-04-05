package me.thegiggitybyte.sleepwarp;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TickMonitor {
    static class Measurement {
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
    private static final int MAX_MEASUREMENT_COUNT = 30;
    
    private BigDecimal averageTickRate, averageTicksSkipped;
    private AtomicInteger currentTick;
    private ArrayList<Measurement> measurements;
    
    public TickMonitor() {
        currentTick = new AtomicInteger();
        measurements = new ArrayList<>(MAX_MEASUREMENT_COUNT);
        averageTickRate = TWENTY;
        averageTicksSkipped = BigDecimal.ZERO;
        
        ServerTickEvents.START_SERVER_TICK.register(server -> {
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
            var totalSkipped = BigDecimal.ZERO;
            for (var measurement : measurements)
            {
                totalTicks = totalTicks.add(measurement.totalTickCount);
                totalSkipped = totalSkipped.add(measurement.skippedTickCount);
            }
            
            var sampleSize = BigDecimal.valueOf(measurements.size());
            averageTickRate = totalTicks.divide(sampleSize, RoundingMode.HALF_EVEN);
            averageTicksSkipped = totalSkipped.divide(sampleSize, RoundingMode.HALF_EVEN);
        });
    }
    
    public int getCurrentTick() {
        return currentTick.get();
    }
    
    public BigDecimal getAverageTickRate() {
        return averageTickRate.setScale(0, RoundingMode.HALF_EVEN);
    }
    
    public BigDecimal getAverageTicksSkipped() {
        return averageTicksSkipped.setScale(0, RoundingMode.HALF_EVEN);
    }
}
