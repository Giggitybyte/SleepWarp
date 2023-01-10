package me.thegiggitybyte.sleepwarp;

import com.google.common.collect.EvictingQueue;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TickMonitor {
    private static final BigDecimal TWENTY_BILLION_NANOSECONDS = BigDecimal.valueOf(TimeUnit.SECONDS.toNanos(20));
    
    private long lastMeasurementNanoseconds;
    private BigDecimal averageTicksPerSecond;
    private AtomicInteger currentTick;
    private Queue<BigDecimal> recentMeasurements;
    
    public TickMonitor() {
        currentTick = new AtomicInteger();
        recentMeasurements = EvictingQueue.create(5);
        averageTicksPerSecond = BigDecimal.valueOf(20);
        
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            var currentTimeNanoseconds = System.nanoTime();
            measureTickDuration(currentTimeNanoseconds);
        });
    }
    
    private void measureTickDuration(long tickTimeNanoseconds) {
        if (currentTick.getAndIncrement() % 20 != 0) return;
        
        if (lastMeasurementNanoseconds > 0) {
            var processingTimeNanoseconds = BigDecimal.valueOf(tickTimeNanoseconds - lastMeasurementNanoseconds);
            var ticksPerSecond = TWENTY_BILLION_NANOSECONDS.divide(processingTimeNanoseconds, 30, RoundingMode.HALF_UP);
            
            recentMeasurements.add(ticksPerSecond);
            
            var total = BigDecimal.ZERO;
            for (var measurement : recentMeasurements)
                total = total.add(measurement);
            
            var sampleSize = BigDecimal.valueOf(recentMeasurements.size());
            averageTicksPerSecond = total.divide(sampleSize, RoundingMode.HALF_UP);
        }
        
        lastMeasurementNanoseconds = tickTimeNanoseconds;
    }
    
    public BigDecimal getAverageTickRate() {
        return averageTicksPerSecond.setScale(0, RoundingMode.HALF_UP);
    }
    
    public int getAverageTickLoss() {
        return BigDecimal.valueOf(20).remainder(getAverageTickRate()).setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
