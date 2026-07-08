package dev.leonetic.features.modules.player;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.TickEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.modules.combat.OffhandModule;
import dev.leonetic.features.modules.combat.PhaseModule;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.manager.RotationRequest;
import dev.leonetic.manager.SwapRequest;
import dev.leonetic.mixin.client.ClientLevelAccessor;
import dev.leonetic.util.EnchantmentUtil;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import static dev.leonetic.util.inventory.InventoryUtil.FULL_SCOPE;

public class AutoXPModule extends Module {

    private final Setting<Boolean> pauseInAir = bool("PauseInAir", true);

    private static final int THROWS_PER_BATCH = 6;
    private static final long BATCH_INTERVAL_MS = 300;

    private long lastThrowMs;

    public AutoXPModule() {
        super("AutoXP", "Throws XP bottles in bursts until your mending armor is fully repaired.", Category.PLAYER);
    }

    @Override
    public void onDisable() {
        lastThrowMs = 0;
    }

    @Subscribe
    private void onTick(TickEvent event) {
        if (nullCheck() || mc.screen != null) return;

        OffhandModule offhand = Homovore.moduleManager.getModuleByClass(OffhandModule.class);
        if (offhand != null && offhand.shouldDeferForEat()) return;

        PhaseModule phase = Homovore.moduleManager.getModuleByClass(PhaseModule.class);
        if (phase != null && phase.isEnabled()) return;

        if (!hasDamagedMendingArmor()) {
            disable();
            return;
        }

        if (pauseInAir.getValue() && !mc.player.onGround()) return;

        Result xpBottle = InventoryUtil.find(Items.EXPERIENCE_BOTTLE, FULL_SCOPE);
        if (!xpBottle.found()) return;

        float yaw = mc.player.getYRot();
        float pitch = 90f;
        Homovore.rotationManager.submit(new RotationRequest(
                "AutoXP", 40, yaw, pitch, RotationRequest.Mode.SILENT
        ));

        if (!shouldThrowNow()) return;

        mc.gameMode.ensureHasSentCarriedItem();

        Homovore.swapManager.submit(new SwapRequest("AutoXP", 40, xpBottle, r -> {
            for (int i = 0; i < THROWS_PER_BATCH; i++) {
                try (var handler = ((ClientLevelAccessor) mc.level).homovore$getBlockStatePredictionHandler().startPredicting()) {
                    mc.getConnection().send(new ServerboundUseItemPacket(r.hand(), handler.currentSequence(), yaw, pitch));
                }
            }
        }, true));
    }

    private boolean shouldThrowNow() {
        long now = System.currentTimeMillis();
        if (now - lastThrowMs < BATCH_INTERVAL_MS) return false;
        lastThrowMs = now;
        return true;
    }

    private boolean hasDamagedMendingArmor() {
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack armor = mc.player.getItemBySlot(slot);
            if (armor.isEmpty() || armor.getMaxDamage() <= 0) continue;
            if (!EnchantmentUtil.has(Enchantments.MENDING, armor)) continue;
            if (armor.getDamageValue() > 0) return true;
        }
        return false;
    }
}
