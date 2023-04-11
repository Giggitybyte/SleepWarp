package me.thegiggitybyte.sleepwarp.utility;

import me.thegiggitybyte.sleepwarp.config.JsonConfiguration;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.mob.SkeletonHorseEntity;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.*;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles incrementing time and simulating the world.
 * Contains methods for each action that the vanilla tick does.
 */
public class WarpDrive {
    private static final int DAY_LENGTH = 24000;
    
    /**
     * Accelerates time by several ticks if all conditions are met to sleep.
     */
    public static <TWorld extends World> void tryJump(TWorld world) {
        // Pre-warp checks.
        if (world instanceof ServerWorld == false) throw new AssertionError("Unexpected world type.");
        var serverWorld = (ServerWorld)world;
        
        var totalPlayers = serverWorld.getPlayers().size();
        var sleepingPlayers = serverWorld.getPlayers().stream().filter(player -> player.canResetTimeBySleeping()).count();
        if (sleepingPlayers == 0) return;
        
        if (JsonConfiguration.getUserInstance().getValue("use_sleep_percentage").getAsBoolean()) {
            var percentRequired = serverWorld.getGameRules().getInt(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
            var minimumSleeping = Math.max(1, (totalPlayers * percentRequired) / 100);
            if (sleepingPlayers < minimumSleeping) return;
        }
        
        // Calculate amount of ticks to add to time.
        var maxTicksAdded = Math.max(10, JsonConfiguration.getUserInstance().getValue("max_ticks_added").getAsInt());
        var playerMultiplier = Math.max(0.05, Math.min(1.0, JsonConfiguration.getUserInstance().getValue("player_multiplier").getAsDouble()));
        var worldTime = serverWorld.getTimeOfDay() % DAY_LENGTH;
        long ticksAdded;
        
        if (worldTime + maxTicksAdded < DAY_LENGTH) {
            var sleepingRatio = (double) sleepingPlayers / totalPlayers;
            var scaledRatio = sleepingRatio * playerMultiplier;
            var tickMultiplier = scaledRatio / ((scaledRatio * 2) - playerMultiplier - sleepingRatio + 1);
            
            ticksAdded = Math.round(maxTicksAdded * tickMultiplier);
        } else {
            ticksAdded = DAY_LENGTH % worldTime;
        }
        
        // Remove some ticks if the server is overloaded.
        var performanceMode = JsonConfiguration.getUserInstance().getValue("performance_mode").getAsBoolean();
        var averageTickRate = TickMonitor.getInstance().getAverageTickRate();
        var averageTicksSkipped = TickMonitor.getInstance().getAverageTickLoss();
        
        if (performanceMode && averageTicksSkipped > 1) {
            var tickMultiplier = Math.pow(averageTicksSkipped, 1.17) / averageTickRate;
            var ticksRemoved = Math.round(ticksAdded * tickMultiplier);
            
            ticksAdded -= ticksRemoved;
        }
        
        // All systems nominal, warp power available.
        WarpDrive.engage(serverWorld, ticksAdded);
        
        // Wake players if needed, display sleep status.
        var actionBarText = Text.empty().formatted(Formatting.WHITE);
        worldTime = serverWorld.getTimeOfDay() % DAY_LENGTH;
        
        if (worldTime > 0) {
            long remainingTicks = serverWorld.isThundering()
                    ? serverWorld.worldProperties.getThunderTime()
                    : DAY_LENGTH - worldTime;
            
            if (remainingTicks > 0) {
                var requiredPercentage = 1.0 - playerMultiplier;
                var actualPercentage = (double) sleepingPlayers / totalPlayers;
                var hourglassEmojiColor = actualPercentage >= requiredPercentage
                        ? Formatting.DARK_GREEN
                        : Formatting.RED;
                
                actionBarText.append(Text.literal("⌛ ").formatted(hourglassEmojiColor));
                
                var remainingSeconds = BigDecimal.valueOf(remainingTicks)
                        .divide(BigDecimal.valueOf(ticksAdded), 3, RoundingMode.HALF_UP)
                        .divide(BigDecimal.valueOf(averageTickRate), 3, RoundingMode.HALF_UP)
                        .setScale(1, RoundingMode.HALF_EVEN)
                        .toPlainString();
                
                actionBarText.append(Text.literal(remainingSeconds))
                        .append(" seconds until ")
                        .append(world.isThundering() ? "the thunderstorm passes" : "dawn");
            }
        } else {
            if (serverWorld.isRaining()) serverWorld.resetWeather();
            serverWorld.wakeSleepingPlayers();
            
            var currentDay = BigDecimal.valueOf(world.getTime()).divide(BigDecimal.valueOf(DAY_LENGTH), 0, RoundingMode.HALF_UP).toPlainString();
            actionBarText.append("Day ").append(Text.literal(currentDay).formatted(Formatting.GOLD));
        }
        
        serverWorld.getPlayers().forEach(player -> player.sendMessage(actionBarText, true));
    }
    
    /**
     * <img width="327" height="250" src="https://i.imgur.com/DMUv8R3.jpg"/><br/><br/>
     * Simulates the passage of time by ticking the world multiple times.
     * */
    static void engage(ServerWorld world, long tickCount) {
        var chunkStorage = world.getChunkManager().threadedAnvilChunkStorage;
        var chunkHolderPairs = new ArrayList<Pair<WorldChunk, ChunkHolder>>();
        
        for (ChunkHolder chunkHolder : chunkStorage.entryIterator()) {
            WorldChunk chunk = chunkHolder.getWorldChunk();
            
            if (chunk != null && world.shouldTick(chunk.getPos()) && chunkStorage.shouldTick(chunk.getPos())) {
                chunkHolderPairs.add(new Pair<>(chunk, chunkHolder));
            }
        }
        
        var averageTicksSkipped = TickMonitor.getInstance().getAverageTickLoss();
        var canTickEntities = JsonConfiguration.getUserInstance().getValue("tick_entities").getAsBoolean();
        
        for (var tick = 0; tick < tickCount; tick++) {
            world.inBlockTick = true;
            
            world.tickWeather();
            world.calculateAmbientDarkness();
            world.tickTime();
            
            var doDaylightCycle = world.worldProperties.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE);
            var packet = new WorldTimeUpdateS2CPacket(world.getTime(), world.getTimeOfDay(), doDaylightCycle);
            world.getServer().getPlayerManager().sendToDimension(packet, world.getRegistryKey());
            
            world.getBlockTickScheduler().tick(world.getTime(), 65536, (pos, block) -> {
                var blockState = world.getBlockState(pos);
                if (blockState.isOf(block)) blockState.scheduledTick(world, pos, world.getRandom());
            });
            
            world.getFluidTickScheduler().tick(world.getTime(), 65536, (pos, fluid) -> {
                var fluidState = world.getFluidState(pos);
                if (fluidState.isOf(fluid)) fluidState.onScheduledTick(world, pos);
            });
            
            tickWorld(world, chunkHolderPairs, averageTicksSkipped);
            chunkStorage.tickEntityMovement();
            world.processSyncedBlockEvents();
            
            world.inBlockTick = false;
            
            if (canTickEntities) {
                world.entityList.forEach(entity -> {
                    if (entity.isRemoved()) return;
                    
                    if (world.shouldCancelSpawn(entity))
                        entity.discard();
                    else
                        tickEntity(world, entity);
                });
            }
            
            if (JsonConfiguration.getUserInstance().getValue("tick_block_entities").getAsBoolean()) {
                world.tickBlockEntities();
            }
            
            world.entityManager.tick();
        }
    }
    
    private static void tickWorld(ServerWorld world, List<Pair<WorldChunk, ChunkHolder>> chunkHolderPairs, int averageTicksSkipped) {
        boolean canTickMobSpawn = JsonConfiguration.getUserInstance().getValue("tick_mob_spawn").getAsBoolean();
        boolean canTickLightning = JsonConfiguration.getUserInstance().getValue("tick_lightning").getAsBoolean();
        boolean canTickIceFreezing = JsonConfiguration.getUserInstance().getValue("tick_ice_freezing").getAsBoolean();
        boolean canTickSnow = JsonConfiguration.getUserInstance().getValue("tick_snow_accumulation").getAsBoolean();
        boolean canTickRandomBlock = JsonConfiguration.getUserInstance().getValue("tick_random_block").getAsBoolean();
        boolean canTickSpawners = JsonConfiguration.getUserInstance().getValue("tick_spawners").getAsBoolean();
        
        Collections.shuffle(chunkHolderPairs);
        for (var pair : chunkHolderPairs) {
            WorldChunk chunk = pair.getLeft();
            
            if (canTickMobSpawn) tickMobSpawn(world, chunk);
            if (canTickLightning) tickLightning(world, chunk);
            
            if (world.getRandom().nextInt(16) == 0) {
                var randomPos = world.getRandomPosInChunk(chunk.getPos().getStartX(), 0, chunk.getPos().getStartZ(), 15);
                var topBlockPos = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, randomPos);
                var biome = world.getBiome(topBlockPos).value();
                
                if (canTickIceFreezing && biome.canSetIce(world, topBlockPos.down()))
                    world.setBlockState(topBlockPos.down(), Blocks.ICE.getDefaultState());
                
                if (world.isRaining()) {
                    if (canTickSnow) tickSnowAccumulation(world, biome, topBlockPos);
                    
                    var precipitation = biome.getPrecipitation(topBlockPos.down());
                    if (precipitation != Biome.Precipitation.NONE) {
                        var blockState = world.getBlockState(topBlockPos.down());
                        blockState.getBlock().precipitationTick(blockState, world, topBlockPos.down(), precipitation);
                    }
                }
            }
            
            if (canTickRandomBlock) tickRandomBlock(world, chunk);
        }
        
        if (canTickSpawners) tickSpawners(world);
        
        for (var pair : chunkHolderPairs) {
            WorldChunk chunk = pair.getLeft();
            ChunkHolder holder = pair.getRight();
            
            holder.flushUpdates(chunk);
        }
    }
    
    private static void tickEntity(ServerWorld world, Entity entity) {
        entity.checkDespawn();
        if (world.shouldTickEntity(entity.getBlockPos()) == false) return;
        
        Entity entityVehicle = entity.getVehicle();
        if (entityVehicle != null) {
            if (entityVehicle.isRemoved() || entityVehicle.hasPassenger(entity) == false) {
                entity.stopRiding();
            }
        }
        
        world.tickEntity(entity);
    }
    
    private static void tickMobSpawn(ServerWorld world, WorldChunk chunk) {
        if (world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING) == false) return;
        
        boolean mobSpawningEnabled = world.getServer().isMonsterSpawningEnabled();
        boolean animalSpawningEnabled =  world.getServer().shouldSpawnAnimals();
        
        if ((mobSpawningEnabled || animalSpawningEnabled) && world.getWorldBorder().contains(chunk.getPos())) {
            var chunkCount = world.getChunkManager().getLoadedChunkCount();
            var densityCapper = new SpawnDensityCapper(world.getChunkManager().threadedAnvilChunkStorage);
            var spawnInfo = SpawnHelper.setupSpawn(chunkCount, world.iterateEntities(), (pos, consumer) -> consumer.accept(chunk), densityCapper);
            boolean rareSpawn = world.getLevelProperties().getTime() % 400 == 0;
            
            SpawnHelper.spawn(world, chunk, spawnInfo, animalSpawningEnabled, mobSpawningEnabled, rareSpawn);
        }
    }
    
    private static void tickLightning(ServerWorld world, WorldChunk chunk) {
        if (world.isRaining() == false || world.isThundering() == false || world.random.nextInt(100000) > 0) return;
        
        var chunkPos = chunk.getPos();
        var randomPos = world.getRandomPosInChunk(chunkPos.getStartX(), 0, chunkPos.getStartZ(), 15);
        var blockPos = world.getLightningPos(randomPos);
        
        if (world.hasRain(blockPos) == false) return;
        
        var canSpawnMobs = world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING);
        var localDifficulty = world.getLocalDifficulty(blockPos).getLocalDifficulty() * 0.01;
        boolean skeletonHorseSpawn = canSpawnMobs && (world.random.nextDouble() < localDifficulty) && !world.getBlockState(blockPos.down()).isOf(Blocks.LIGHTNING_ROD);
        
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
    
    private static void tickSnowAccumulation(ServerWorld world, Biome biome, BlockPos blockPos) {
        var layerHeight = world.getGameRules().getInt(GameRules.SNOW_ACCUMULATION_HEIGHT);
        if (layerHeight == 0 || biome.canSetSnow(world, blockPos) == false) return;
        
        var blockState = world.getBlockState(blockPos);
        if (blockState.isOf(Blocks.SNOW)) {
            int snowLayers = blockState.get(SnowBlock.LAYERS);
            if (snowLayers < Math.min(layerHeight, 8)) {
                var layerBlockState = blockState.with(SnowBlock.LAYERS, snowLayers + 1);
                Block.pushEntitiesUpBeforeBlockChange(blockState, layerBlockState, world, blockPos);
                world.setBlockState(blockPos, layerBlockState);
            }
        } else {
            world.setBlockState(blockPos, Blocks.SNOW.getDefaultState());
        }
    }
    
    private static void tickSpawners(ServerWorld world) {
        if (world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING) == false) return;
        
        boolean mobSpawningEnabled = world.getServer().isMonsterSpawningEnabled();
        boolean animalSpawningEnabled =  world.getServer().shouldSpawnAnimals();
        world.tickSpawners(mobSpawningEnabled, animalSpawningEnabled);
    }
    
    private static void tickRandomBlock(ServerWorld world, WorldChunk chunk) {
        var tickCount = world.getGameRules().getInt(GameRules.RANDOM_TICK_SPEED);
        if (tickCount == 0) return;
        
        var startX = chunk.getPos().getStartX();
        var startZ = chunk.getPos().getStartZ();
        for (ChunkSection chunkSection : chunk.getSectionArray()) {
            if (chunkSection.hasRandomTicks() == false) continue;
            
            var startY = chunkSection.getYOffset();
            for(int tick = 0; tick < tickCount; ++tick) {
                var blockPos = world.getRandomPosInChunk(startX, startY, startZ, 15);
                var blockState = chunkSection.getBlockState(blockPos.getX() - startX, blockPos.getY() - startY, blockPos.getZ() - startZ);
                if (blockState.hasRandomTicks()) blockState.randomTick(world, blockPos, world.getRandom());
                
                var fluidState = blockState.getFluidState();
                if (fluidState.hasRandomTicks()) fluidState.onRandomTick(world, blockPos, world.getRandom());
            }
        }
    }
}
