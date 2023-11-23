package me.thegiggitybyte.sleepwarp.runnable;

import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.mob.SkeletonHorseEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Random;

public class LightningTickRunnable implements Runnable {
    private final ServerWorld world;
    private final WorldChunk chunk;
    
    public LightningTickRunnable(ServerWorld world, WorldChunk chunk) {
        this.world = world;
        this.chunk = chunk;
    }
    
    @Override
    public void run() {
        var chunkPos = chunk.getPos();
        var randomPos = world.getRandomPosInChunk(chunkPos.getStartX(), 0, chunkPos.getStartZ(), 15);
        var blockPos = world.getLightningPos(randomPos);
        
        var canSpawnMobs = world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING);
        var localDifficulty = world.getLocalDifficulty(blockPos).getLocalDifficulty() * 0.01;
        boolean skeletonHorseSpawn = canSpawnMobs && (new Random().nextDouble() < localDifficulty) && !world.getBlockState(blockPos.down()).isOf(Blocks.LIGHTNING_ROD);
        
        if (skeletonHorseSpawn) {
            SkeletonHorseEntity skeletonHorseEntity = EntityType.SKELETON_HORSE.create(world);
            if (skeletonHorseEntity != null) {
                skeletonHorseEntity.setTrapped(true);
                skeletonHorseEntity.setBreedingAge(0);
                skeletonHorseEntity.setPosition(blockPos.getX(), blockPos.getY(), blockPos.getZ());
                world.spawnEntity(skeletonHorseEntity);
            }
        }
        
        LightningEntity lightningEntity = EntityType.LIGHTNING_BOLT.create(world);
        if (lightningEntity != null) {
            lightningEntity.refreshPositionAfterTeleport(Vec3d.ofBottomCenter(blockPos));
            lightningEntity.setCosmetic(skeletonHorseSpawn);
            world.spawnEntity(lightningEntity);
        }
    }
}
