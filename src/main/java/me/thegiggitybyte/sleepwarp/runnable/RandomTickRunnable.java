package me.thegiggitybyte.sleepwarp.runnable;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameRules;
import net.minecraft.world.chunk.WorldChunk;

public class RandomTickRunnable implements Runnable {
    private final ServerWorld world;
    private final WorldChunk chunk;
    private Random random;
    
    public  RandomTickRunnable(ServerWorld world, WorldChunk chunk) {
        this.world = world;
        this.chunk = chunk;
        random = Random.create();
    }
    
    @Override
    public void run() {
        var startX = chunk.getPos().getStartX();
        var startZ = chunk.getPos().getStartZ();
        var chunkSections = chunk.getSectionArray();
        
        for (var sectionIndex = 0; sectionIndex < chunkSections.length; ++sectionIndex) {
            var chunkSection = chunkSections[sectionIndex];
            if (!chunkSection.hasRandomTicks()) continue;
            
            var sectionCoordinate = chunk.sectionIndexToCoord(sectionIndex);
            var startY = ChunkSectionPos.getBlockCoord(sectionCoordinate);
            
            for(int i = 0; i < world.getGameRules().getInt(GameRules.RANDOM_TICK_SPEED); ++i) {
                var blockPos = world.getRandomPosInChunk(startX, startY, startZ, 15);
                var blockState = chunkSection.getBlockState(blockPos.getX() - startX , blockPos.getY() - startY, blockPos.getZ() - startZ);
                var fluidState = blockState.getFluidState();
                
                if (blockState.hasRandomTicks()) blockState.randomTick(world, blockPos, random);
                if (fluidState.hasRandomTicks()) fluidState.onRandomTick(world, blockPos, random);
            }
        }
    }
}
