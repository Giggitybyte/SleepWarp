package me.thegiggitybyte.sleepwarp;

import dev.architectury.event.events.common.InteractionEvent;
import dev.architectury.event.events.common.TickEvent;
import me.thegiggitybyte.sleepwarp.config.JsonConfiguration;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles incrementing time and simulating the world.
 * Contains methods for each action that the vanilla tick does.
 */
public class WarpDrive {
    public static final String MOD_ID = "sleepwarp";
    private static final int DAY_LENGTH_TICKS;
    private static final ExecutorService TICK_EXECUTOR, RANDOM_TICK_EXECUTOR, MOB_TICK_EXECUTOR;
    
    public static void init() {
        TickEvent.SERVER_LEVEL_POST.register(level -> {
            var sleepingPlayers = level.players().stream().filter(Player::isSleepingLongEnough).count();
            if (sleepingPlayers > 0) WarpDrive.engage(level);
        });
        
        InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, face) -> {
            if (player instanceof ServerPlayer == false) return;
            var serverPlayer = (ServerPlayer) player;
            var serverLevel = serverPlayer.getLevel();
            
            if (serverLevel.getBlockState(pos).is(BlockTags.BEDS) && serverLevel.canSleepThroughNights() && serverPlayer.startSleepInBed()) {
            
                
            }
        });
    }
    
    static {
        DAY_LENGTH_TICKS = 24000;
        TICK_EXECUTOR = Executors.newSingleThreadExecutor();
        RANDOM_TICK_EXECUTOR = Executors.newSingleThreadExecutor();
        MOB_TICK_EXECUTOR = Executors.newSingleThreadExecutor();
    }
    
    public static void sendWarpStatus(ServerLevel world) {
        if (world.getServer().isSingleplayer() || world.getServer().isPublished() == false || world.players().size() == 1) return;
        if (JsonConfiguration.getUserInstance().getValue("action_bar_messages").getAsBoolean() == false) return;
        
        long playerCount = 0, inBedCount = 0, sleepingCount = 0;
        
        for (var player : world.getPlayers()) {
            if (player.isSleeping()) {
                if (player.getSleepTimer() >= 100) ++sleepingCount;
                ++inBedCount;
            }
            
            ++playerCount;
        }
        
        var messageText = Text.empty();
        var tallyText = Text.empty()
                .append(Text.literal(String.valueOf(inBedCount)))
                .append("/")
                .append(Text.literal(String.valueOf(playerCount)));
        
        if (inBedCount == 0) {
            messageText.append(tallyText.formatted(Formatting.DARK_GRAY)).append(" players sleeping");
        } else if (JsonConfiguration.getUserInstance().getValue("use_sleep_percentage").getAsBoolean()) {
            var percentRequired = world.getGameRules().getInt(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
            var minSleepingCount = Math.max(1, (playerCount * percentRequired) / 100);
            
            if (sleepingCount < minSleepingCount && minSleepingCount - inBedCount > 0) {
                messageText.append(tallyText.formatted(Formatting.RED))
                        .append(" players sleeping. ")
                        .append(String.valueOf((minSleepingCount - inBedCount)))
                        .append(" more needed to advance time.");
            } else {
                messageText.append(tallyText.formatted(Formatting.DARK_GREEN)).append(" players sleeping");
            }
        } else if (sleepingCount == 0) {
            messageText.append(tallyText.formatted(Formatting.YELLOW)).append(" players sleeping");
        }
        
        if (messageText.getSiblings().size() > 0) {
            world.getPlayers().forEach(player -> player.sendMessage(messageText, true));
        }
    }
    
    /**
     * <img width="327" height="250" src="https://i.imgur.com/DMUv8R3.jpg"/><br/><br/>
     * Accelerates time by several ticks if all conditions are met to sleep.
     */
    static void engage(final ServerLevel world) {
        // Pre-warp checks.
        var totalPlayers = world.players().size();
        var sleepingPlayers = world.players().stream().filter(Player::isSleepingLongEnough).count();
        if (sleepingPlayers == 0) return;
        
        if (JsonConfiguration.getUserInstance().getValue("use_sleep_percentage").getAsBoolean()) {
            var percentRequired = world.getGameRules().getInt(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
            var minimumSleeping = Math.max(1, (totalPlayers * percentRequired) / 100);
            if (sleepingPlayers < minimumSleeping) return;
        }
        
        // Determine amount of ticks to add to time.
        var maxTicksAdded = Math.max(10, JsonConfiguration.getUserInstance().getValue("max_ticks_added").getAsInt());
        var playerMultiplier = Math.max(0.05, Math.min(1.0, JsonConfiguration.getUserInstance().getValue("player_multiplier").getAsDouble()));
        var worldTime = world.getTimeOfDay() % DAY_LENGTH_TICKS;
        int tickCount;
        
        if (worldTime + maxTicksAdded < DAY_LENGTH_TICKS) {
            if (totalPlayers == 1) {
                tickCount = maxTicksAdded;
            } else {
                var sleepingRatio = (double) sleepingPlayers / totalPlayers;
                var scaledRatio = sleepingRatio * playerMultiplier;
                var tickMultiplier = scaledRatio / ((scaledRatio * 2) - playerMultiplier - sleepingRatio + 1);
                
                tickCount = Math.toIntExact(Math.round(maxTicksAdded * tickMultiplier));
            }
        } else {
            tickCount = Math.toIntExact(DAY_LENGTH_TICKS % worldTime);
        }
        
        // Advance time.
        for (var tick = 0; tick < tickCount; tick++) {
            world.tickWeather();
            world.calculateAmbientDarkness();
            world.tickTime();
        }
        
        // Update player clients.
        var doDaylightCycle = world.worldProperties.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE);
        var packet = new WorldTimeUpdateS2CPacket(world.getTime(), world.getTimeOfDay(), doDaylightCycle);
        world.getServer().getPlayerManager().sendToDimension(packet, world.getRegistryKey());
        
        // Wake players if day, otherwise tick world.
        var currentWorldTime = world.getTimeOfDay() % DAY_LENGTH_TICKS;
        
        if (currentWorldTime == 0) {
            if (world.isRaining()) world.resetWeather();
            world.wakeSleepingPlayers();
        } else {
            tickWorld(world, tickCount);
        }
        
        // Send update message.
        if (JsonConfiguration.getUserInstance().getValue("action_bar_messages").getAsBoolean() == false) return;
        
        var actionBarText = Text.empty().formatted(Formatting.WHITE);
        if (currentWorldTime > 0) {
            var remainingTicks = world.isThundering()
                    ? world.worldProperties.getThunderTime()
                    : DAY_LENGTH_TICKS - currentWorldTime;
            
            if (remainingTicks > 0) {
                if (totalPlayers > 1) {
                    var requiredPercentage = 1.0 - playerMultiplier;
                    var actualPercentage = (double) sleepingPlayers / totalPlayers;
                    var indicatorColor = actualPercentage >= requiredPercentage ? Formatting.DARK_GREEN : Formatting.RED;
                    var playerNoun = (sleepingPlayers == 1 ? "player" : "players");
                    
                    actionBarText.append(Text.literal("⌛ " + sleepingPlayers + ' ').formatted(indicatorColor));
                    actionBarText.append(Text.literal(playerNoun + " sleeping. "));
                } else {
                    actionBarText.append(Text.literal("⌛ ").formatted(Formatting.GOLD));
                }
                
                var remainingSeconds = Math.round(((double) remainingTicks / tickCount) / 20); // TODO: delta between last warp.
                actionBarText.append(Text.literal(String.valueOf(remainingSeconds)))
                        .append(" seconds until ")
                        .append(world.isThundering() ? "the thunderstorm passes" : "dawn");
            }
        } else {
            var currentDay = String.valueOf(world.getTimeOfDay() / DAY_LENGTH_TICKS);
            actionBarText.append("Day ").append(Text.literal(currentDay).formatted(Formatting.GOLD));
        }
        
        world.getPlayers().forEach(player -> player.sendMessage(actionBarText, true));
    }
    
    /**
     * Simulates the passage of time by ticking the world multiple times.
     */
    static void tickWorld(final ServerWorld world, int tickCount) {
        var chunkStorage = world.getChunkManager().threadedAnvilChunkStorage;
        var chunks = new ArrayList<WorldChunk>();
        
        for (ChunkHolder chunkHolder : chunkStorage.entryIterator()) {
            WorldChunk chunk = chunkHolder.getWorldChunk();
            
            if (chunk != null && world.shouldTick(chunk.getPos()) && chunkStorage.shouldTick(chunk.getPos())) {
                chunks.add(chunk);
            }
        }
        
        var canTickRandomBlock = JsonConfiguration.getUserInstance().getValue("tick_random_block").getAsBoolean();
        var canTickBlockEntities = JsonConfiguration.getUserInstance().getValue("tick_block_entities").getAsBoolean();
        var canTickLightning = JsonConfiguration.getUserInstance().getValue("tick_lightning").getAsBoolean();
        var random = Random.create();
        
        for (var tick = 0; tick < tickCount; tick++) {
            Collections.shuffle(chunks);
            
            for (var chunk : chunks) {
                if (canTickRandomBlock) CompletableFuture.runAsync(() -> tickRandomBlock(world, chunk), RANDOM_TICK_EXECUTOR);
                
                if (canTickLightning && world.isRaining() && world.isThundering() && random.nextInt(100000) == 0)
                    CompletableFuture.runAsync(() -> tickLightning(world, chunk), TICK_EXECUTOR);
                
                if (random.nextInt(16) == 0) CompletableFuture.runAsync(() -> tickPrecipitation(world, chunk), TICK_EXECUTOR);
            }
            
            if (canTickBlockEntities) CompletableFuture.runAsync(() -> tickBlockEntities(world), TICK_EXECUTOR);
        }
        
        boolean canTickAnimals = JsonConfiguration.getUserInstance().getValue("tick_animals").getAsBoolean();
        boolean canTickMonsters = JsonConfiguration.getUserInstance().getValue("tick_monsters").getAsBoolean();
        
        if (canTickAnimals | canTickMonsters) {
            var animals = new ArrayList<MobEntity>();
            var monsters = new ArrayList<MobEntity>();
            
            world.entityList.forEach(entity -> {
                if (entity.isRemoved()) return;
                
                if (canTickAnimals && entity instanceof AnimalEntity animal)
                    animals.add(animal);
                else if (canTickMonsters && entity instanceof HostileEntity monster)
                    monsters.add(monster);
            });
            
            if (canTickAnimals) {
                var animalTickMultiplier = JsonConfiguration.getUserInstance().getValue("animal_tick_multiplier").getAsDouble();
                for (var tick = 0; tick < tickCount * animalTickMultiplier; tick++) {
                    CompletableFuture.runAsync(() -> tickMobs(world, animals), MOB_TICK_EXECUTOR);
                }
            }
            
            if (canTickMonsters) {
                var monsterTickMultiplier = JsonConfiguration.getUserInstance().getValue("monster_tick_multiplier").getAsDouble();
                for (var tick = 0; tick < tickCount * monsterTickMultiplier; tick++) {
                    CompletableFuture.runAsync(() -> tickMobs(world, monsters), MOB_TICK_EXECUTOR);
                }
            }
        }
    }
    
    private static void tickPrecipitation(final ServerWorld world, WorldChunk chunk) {
        var randomPos = world.getRandomPosInChunk(chunk.getPos().getStartX(), 0, chunk.getPos().getStartZ(), 15);
        var topBlockPos = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, randomPos);
        var biome = world.getBiome(topBlockPos).value();
        
        if (JsonConfiguration.getUserInstance().getValue("tick_ice_freezing").getAsBoolean() && biome.canSetIce(world, topBlockPos.down()))
            world.setBlockState(topBlockPos.down(), Blocks.ICE.getDefaultState());
        
        if (world.isRaining()) {
            if (JsonConfiguration.getUserInstance().getValue("tick_snow_accumulation").getAsBoolean())
                tickSnowAccumulation(world, biome, topBlockPos);
            
            var precipitation = biome.getPrecipitation(topBlockPos.down());
            if (precipitation != Biome.Precipitation.NONE) {
                var blockState = world.getBlockState(topBlockPos.down());
                blockState.getBlock().precipitationTick(blockState, world, topBlockPos.down(), precipitation);
            }
        }
    }
    
    private static void tickSnowAccumulation(final ServerWorld world, final Biome biome, final BlockPos blockPos) {
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
    
    private static void tickMobs(final ServerWorld world, List<MobEntity> entities) {
        Collections.shuffle(entities);
        
        for (MobEntity entity : entities) {
            world.getServer().submit(() -> {
                if (entity.isRemoved() || world.shouldCancelSpawn(entity) | world.shouldTickEntity(entity.getBlockPos()) == false) return;
                
                Entity entityVehicle = entity.getVehicle();
                if (entityVehicle != null && (entityVehicle.isRemoved() || entityVehicle.hasPassenger(entity) == false)) {
                    entity.stopRiding();
                }
                
                world.tickEntity(entity);
            });
        }
    }
    
    private static void tickBlockEntities(final ServerWorld world) {
        for (BlockEntityTickInvoker tickInvoker : world.blockEntityTickers) {
            if (tickInvoker.isRemoved() == false && world.shouldTickBlockPos(tickInvoker.getPos())) {
                tickInvoker.tick();
            }
        }
    }
    
    private static void tickLightning(final ServerWorld world, final WorldChunk chunk) {
        var chunkPos = chunk.getPos();
        var randomPos = world.getRandomPosInChunk(chunkPos.getStartX(), 0, chunkPos.getStartZ(), 15);
        var blockPos = world.getLightningPos(randomPos);
        
        if (world.hasRain(blockPos) == false) return;
        
        var canSpawnMobs = world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING);
        var localDifficulty = world.getLocalDifficulty(blockPos).getLocalDifficulty() * 0.01;
        boolean skeletonHorseSpawn = canSpawnMobs && (Random.create().nextDouble() < localDifficulty) && !world.getBlockState(blockPos.down()).isOf(Blocks.LIGHTNING_ROD);
        
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
    
    private static void tickRandomBlock(final ServerWorld world, final WorldChunk chunk) {
        var tickCount = world.getGameRules().getInt(GameRules.RANDOM_TICK_SPEED);
        if (tickCount == 0) return;
        
        var random = Random.create();
        var startX = chunk.getPos().getStartX();
        var startZ = chunk.getPos().getStartZ();
        
        var sectionIndex = 0;
        for (ChunkSection chunkSection : chunk.getSectionArray()) {
            if (chunkSection.hasRandomTicks() == false) continue;
            
            var bottomY = chunk.sectionIndexToCoord(sectionIndex++);
            var startY = ChunkSectionPos.getBlockCoord(bottomY);
            
            for (int tick = 0; tick < tickCount; ++tick) {
                var blockPos = world.getRandomPosInChunk(startX, startY, startZ, 15);
                var blockState = chunkSection.getBlockState(blockPos.getX() - startX, blockPos.getY() - startY, blockPos.getZ() - startZ);
                if (blockState.hasRandomTicks()) blockState.randomTick(world, blockPos, random);
                
                var fluidState = blockState.getFluidState();
                if (fluidState.hasRandomTicks()) fluidState.onRandomTick(world, blockPos, random);
            }
        }
    }
}
