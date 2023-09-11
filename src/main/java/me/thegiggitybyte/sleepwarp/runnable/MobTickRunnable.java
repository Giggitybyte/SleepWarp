package me.thegiggitybyte.sleepwarp.runnable;

import me.thegiggitybyte.sleepwarp.config.JsonConfiguration;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MobTickRunnable implements Runnable {
    private final ServerWorld world;
    private final int tickCount;
    
    public MobTickRunnable(ServerWorld world, int tickCount) {
        this.world = world;
        this.tickCount = tickCount;
    }
    
    @Override
    public void run() {
        var canTickAnimals = JsonConfiguration.getUserInstance().getValue("tick_animals").getAsBoolean();
        var canTickMonsters = JsonConfiguration.getUserInstance().getValue("tick_monsters").getAsBoolean();
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
                tickMobs(animals);
            }
        }
        
        if (canTickMonsters) {
            var monsterTickMultiplier = JsonConfiguration.getUserInstance().getValue("monster_tick_multiplier").getAsDouble();
            for (var tick = 0; tick < tickCount * monsterTickMultiplier; tick++) {
                tickMobs(monsters);
            }
        }
    }
    
    private void tickMobs(List<MobEntity> entities) {
        Collections.shuffle(entities);
        
        for (MobEntity entity : entities) {
            world.getServer().submit(() -> {
                if (entity.isRemoved() || world.shouldCancelSpawn(entity) | !world.shouldTickEntity(entity.getBlockPos())) return;
                
                Entity entityVehicle = entity.getVehicle();
                if (entityVehicle != null && (entityVehicle.isRemoved() || !entityVehicle.hasPassenger(entity))) {
                    entity.stopRiding();
                }
                
                world.tickEntity(entity);
            });
        }
    }
}
