package dev.leonetic.features.modules.movement;

import dev.leonetic.event.impl.entity.player.PreTickEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.mixin.entity.FireworkRocketEntityAccessor;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.ResultType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.Vec3;

import static dev.leonetic.util.inventory.InventoryUtil.FULL_SCOPE;

public class ElytraDashModule extends Module {

    private static final int CHEST_MENU_SLOT = 6;

    private static final double TAKEOFF_VELOCITY = 0.42;

    private static final int OFFHAND_MENU_SLOT = 45;

    private static final int FIRE_TIMEOUT = 40;

    private static final int FRESH_LIFE_TICKS = 6;

    public enum TakeoffMode {
        VELOCITY,
        VANILLA
    }

    private final Setting<TakeoffMode> takeoffMode = mode("Takeoff", TakeoffMode.VELOCITY);
    private final Setting<Integer> minBoostTicks = num("MinBoostTicks", 3, 0, 20);

    private enum Phase {

        EQUIP,

        TAKEOFF,

        FLY
    }

    private Phase phase = Phase.EQUIP;

    private boolean weEquipped;

    private int stowedChestSlot = -1;

    private int ticksSinceRocket = Integer.MAX_VALUE / 2;

    private boolean awaitingRocket;

    public ElytraDashModule() {
        super("ElytraDash", "Swap on your elytra, lift straight into flight, and auto-fire fireworks; swaps back on disable.", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        phase = Phase.EQUIP;
        weEquipped = false;
        stowedChestSlot = -1;
        ticksSinceRocket = Integer.MAX_VALUE / 2;
    }

    @Override
    public void onDisable() {

        if (canClickPlayerInventory() && mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            if (weEquipped) {

                swapChestArmorWith(stowedChestSlot);
            } else {

                Result chestplate = InventoryUtil.find(ElytraDashModule::isChestplate, FULL_SCOPE);
                if (chestplate.found()) swapChestArmorWith(containerSlotOf(chestplate));
            }
        }
        weEquipped = false;
        stowedChestSlot = -1;
    }

    @Subscribe
    private void onPreTick(PreTickEvent event) {
        if (nullCheck()) return;

        switch (phase) {
            case EQUIP -> tickEquip();
            case TAKEOFF -> tickTakeoff();
            case FLY -> tickFly();
        }
    }

    private void tickEquip() {
        if (mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {

            phase = Phase.TAKEOFF;
            return;
        }

        if (!canClickPlayerInventory()) return;

        Result elytra = InventoryUtil.find(Items.ELYTRA, FULL_SCOPE);
        if (!elytra.found()) return;

        int elytraSlot = containerSlotOf(elytra);
        swapChestArmorWith(elytraSlot);
        stowedChestSlot = elytraSlot;
        weEquipped = true;
        phase = Phase.TAKEOFF;
    }

    private void tickTakeoff() {
        if (mc.player.isFallFlying()) {
            enterFlight();
            return;
        }
        if (mc.getConnection() == null) return;
        if (mc.player.onGround()) {
            switch (takeoffMode.getValue()) {
                case VELOCITY -> {
                    Vec3 vel = mc.player.getDeltaMovement();
                    mc.player.setDeltaMovement(vel.x, TAKEOFF_VELOCITY, vel.z);
                    mc.player.setSprinting(true);
                }
                case VANILLA -> mc.player.jumpFromGround();
            }
            return;
        }

        mc.getConnection().send(new ServerboundPlayerCommandPacket(
                mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        mc.player.startFallFlying();
        enterFlight();
    }

    private void tickFly() {
        if (!mc.player.isFallFlying()) {

            phase = Phase.TAKEOFF;
            return;
        }
        ticksSinceRocket++;

        if (awaitingRocket) {
            if (hasFreshAttachedRocket() || ticksSinceRocket >= FIRE_TIMEOUT) {
                awaitingRocket = false;
            } else {
                return;
            }
        }

        if (hasUsefulBoost()) return;

        if (fireRocket()) {
            awaitingRocket = true;
            ticksSinceRocket = 0;
        }
    }

    private boolean hasFreshAttachedRocket() {
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof FireworkRocketEntity rocket)) continue;
            if (((FireworkRocketEntityAccessor) rocket).homovore$getAttachedToEntity() != mc.player) continue;
            if (((FireworkRocketEntityAccessor) rocket).homovore$getLife() <= FRESH_LIFE_TICKS) return true;
        }
        return false;
    }

    private boolean hasUsefulBoost() {
        int bestRemaining = Integer.MIN_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof FireworkRocketEntity rocket)) continue;
            if (((FireworkRocketEntityAccessor) rocket).homovore$getAttachedToEntity() != mc.player) continue;

            int remaining = estimateLifetime(rocket) - ((FireworkRocketEntityAccessor) rocket).homovore$getLife();
            if (remaining > bestRemaining) bestRemaining = remaining;
        }
        return bestRemaining > minBoostTicks.getValue();
    }

    private int estimateLifetime(FireworkRocketEntity rocket) {
        ItemStack stack = rocket.getEntityData().get(FireworkRocketEntityAccessor.homovore$getFireworksItemData());
        Fireworks fireworks = stack.get(DataComponents.FIREWORKS);
        int flight = fireworks != null ? fireworks.flightDuration() : 1;
        return 10 * (flight + 1);
    }

    private void enterFlight() {
        phase = Phase.FLY;
        ticksSinceRocket = Integer.MAX_VALUE / 2;
        awaitingRocket = false;
    }

    private boolean fireRocket() {
        Result rocket = InventoryUtil.find(Items.FIREWORK_ROCKET, FULL_SCOPE);
        if (!rocket.found()) return false;
        if (!canClickPlayerInventory()) return false;

        if (rocket.type() == ResultType.HOTBAR && rocket.slot() == InventoryUtil.selected()) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            return true;
        }

        int containerSlot = containerSlotOf(rocket);
        InventoryUtil.click(containerSlot, InventoryUtil.selected(), ClickType.SWAP);
        try {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        } finally {
            InventoryUtil.click(containerSlot, InventoryUtil.selected(), ClickType.SWAP);
        }
        return true;
    }

    private void swapChestArmorWith(int itemContainerSlot) {
        InventoryUtil.click(itemContainerSlot, 0, ClickType.PICKUP);
        InventoryUtil.click(CHEST_MENU_SLOT, 0, ClickType.PICKUP);
        InventoryUtil.click(itemContainerSlot, 0, ClickType.PICKUP);
    }

    private int containerSlotOf(Result result) {
        if (result.type() == ResultType.OFFHAND) return OFFHAND_MENU_SLOT;
        int slot = result.slot();
        return slot < 9 ? slot + 36 : slot;
    }

    private static boolean isChestplate(ItemStack stack) {
        if (stack.is(Items.ELYTRA)) return false;
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
    }

    private boolean canClickPlayerInventory() {
        return mc.gameMode != null && mc.player.containerMenu == mc.player.inventoryMenu;
    }

    @Override
    public String getDisplayInfo() {
        return switch (phase) {
            case EQUIP -> "Equipping";
            case TAKEOFF -> "Takeoff";
            case FLY -> "Flying";
        };
    }
}
