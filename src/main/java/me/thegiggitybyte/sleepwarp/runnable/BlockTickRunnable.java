package me.thegiggitybyte.sleepwarp.runnable;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.BlockEntityTickInvoker;

public class BlockTickRunnable implements Runnable {
    private final ServerWorld world;
    
    public BlockTickRunnable(ServerWorld world) {
        this.world = world;
    }
    
    @Override
    public void run() {
        for (BlockEntityTickInvoker tickInvoker : world.blockEntityTickers) {
            if (!tickInvoker.isRemoved() && world.shouldTickBlockPos(tickInvoker.getPos())) {
                tickInvoker.tick();
            }
        }
    }
}
