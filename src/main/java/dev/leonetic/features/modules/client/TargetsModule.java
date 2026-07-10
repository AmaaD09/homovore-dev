package dev.leonetic.features.modules.client;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.illager.*;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.skeleton.*;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.*;
import net.minecraft.world.entity.player.Player;

public class TargetsModule extends Module {

    public final Setting<Boolean> players = bool("Players", true);
    public final Setting<Boolean> hostiles = bool("Hostiles", false);
    public final Setting<Boolean> neutrals = bool("Neutrals", false);
    public final Setting<Boolean> passives = bool("Passives", false);

    public TargetsModule() {
        super("Targets", "Configure which entities combat modules target.", Category.CLIENT);
    }

    @Override
    public void onLoad() {
        if (!isEnabled()) enable();
    }

    @Override
    public void onDisable() {
        enable();
    }

    public boolean isValidPlayerTarget(Entity entity) {
        if (!(entity instanceof Player)) return false;
        return isValidTarget(entity);
    }

    public boolean isValidTarget(Entity entity) {
        if (entity == mc.player) return false;
        if (!(entity instanceof LivingEntity living)) return false;
        if (living.isDeadOrDying()) return false;

        if (entity instanceof Player player) {
            return players.getValue() && !Homovore.friendManager.isFriend(player);
        }

        if (hostiles.getValue() && isHostile(entity)) return true;
        if (neutrals.getValue() && isNeutral(entity)) return true;
        if (passives.getValue() && isPassive(entity)) return true;

        return false;
    }

    public static boolean isHostile(Entity e) {
        if (e instanceof Creeper
                || e instanceof Skeleton
                || e instanceof Stray
                || e instanceof WitherSkeleton
                || (e instanceof Zombie && !(e instanceof ZombifiedPiglin))
                || e instanceof Husk
                || e instanceof Drowned
                || e instanceof Vindicator
                || e instanceof Bogged
                || e instanceof Evoker
                || e instanceof Pillager
                || e instanceof Ravager
                || e instanceof Vex
                || e instanceof Breeze
                || e instanceof Blaze
                || e instanceof WitherBoss
                || e instanceof EnderDragon
                || e instanceof Shulker
                || e instanceof Guardian
                || e instanceof ElderGuardian
                || e instanceof Ghast
                || e instanceof Hoglin
                || e instanceof ZombieVillager
                || e instanceof MagmaCube
                || e instanceof Silverfish
                || e instanceof Endermite
                || e instanceof Slime
                || e instanceof Phantom
                || e instanceof Illusioner
                || e instanceof Witch) {
            return true;
        }
        return isNeutralEntityType(e) && isAggressiveNow(e);
    }

    public static boolean isNeutral(Entity e) {
        return isNeutralEntityType(e) && !isAggressiveNow(e);
    }

    public static boolean isPassive(Entity e) {
        return e instanceof AgeableMob
                || (e instanceof IronGolem golem && golem.isPlayerCreated());
    }

    private static boolean isNeutralEntityType(Entity e) {
        return e instanceof EnderMan
                || e instanceof Piglin
                || e instanceof ZombifiedPiglin
                || e instanceof Spider
                || e instanceof CaveSpider
                || e instanceof PolarBear
                || (e instanceof Wolf w && !w.isTame())
                || e instanceof Bee
                || e instanceof Goat
                || (e instanceof IronGolem g && !g.isPlayerCreated());
    }

    private static boolean isAggressiveNow(Entity e) {
        if (e instanceof EnderMan enderman) return enderman.isCreepy();
        if (e instanceof ZombifiedPiglin piglin) return piglin.isAggressive();
        if (e instanceof Piglin piglin) return piglin.isAggressive();
        if (e instanceof Spider spider) return spider.isAggressive();
        if (e instanceof CaveSpider) return true;
        if (e instanceof PolarBear bear) return bear.isAggressive();
        if (e instanceof Wolf wolf) return wolf.isAggressive();
        if (e instanceof Bee bee) return bee.isAngry();
        return false;
    }
}
