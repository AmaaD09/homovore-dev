package dev.leonetic.features.modules.combat;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.TickEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.manager.RotationRequest;
import dev.leonetic.manager.SwapRequest;
import dev.leonetic.mixin.client.ClientLevelAccessor;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class PhaseModule extends Module {

    public PhaseModule() {
        super("Phase", "Phases into walls", Category.COMBAT);
    }

    @Subscribe
    private void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) {
            disable();
            return;
        }

        if (Homovore.rotationManager.isSilentSyncRequired()) return;

        Result pearl = InventoryUtil.find(Items.ENDER_PEARL, InventoryUtil.FULL_SCOPE);
        if (!pearl.found()) {
            disable();
            return;
        }

        if (mc.player.getCooldowns().isOnCooldown(new ItemStack(Items.ENDER_PEARL))) {
            disable();
            return;
        }

        if (mc.player.isCrouching()) {
            disable();
            return;
        }

        Vec3 target = calculateTargetPos();
        float yaw = calcYaw(target);
        float pitch = mc.player.getBlockY() > 4 ? 85f : 75f;

        Homovore.rotationManager.submit(new RotationRequest("phase", 150, yaw, pitch, RotationRequest.Mode.SILENT));

        mc.gameMode.ensureHasSentCarriedItem();
        Homovore.swapManager.submit(new SwapRequest("Phase", 80, pearl, r -> {
            try (var handler = ((ClientLevelAccessor) mc.level).homovore$getBlockStatePredictionHandler().startPredicting()) {
                mc.getConnection().send(new ServerboundUseItemPacket(r.hand(), handler.currentSequence(), yaw, pitch));
            }
        }, true));

        disable();
    }

    private Vec3 calculateTargetPos() {
        double playerX = mc.player.getX();
        double playerZ = mc.player.getZ();

        double nearestIntX = Math.round(playerX);
        double nearestIntZ = Math.round(playerZ);
        double dxCorner = nearestIntX - playerX;
        double dzCorner = nearestIntZ - playerZ;

        if (Math.abs(dxCorner) <= 0.15 && Math.abs(dzCorner) <= 0.15) {
            return new Vec3(
                playerX + Mth.clamp(dxCorner, -0.15, 0.15),
                mc.player.getY() - 0.5,
                playerZ + Mth.clamp(dzCorner, -0.15, 0.15)
            );
        }

        final double A = Math.PI / 13;
        final double B = Math.PI / 4;

        double x = playerX + Mth.clamp(
            toClosest(playerX, Math.floor(playerX) + A, Math.floor(playerX) + B) - playerX,
            -0.2, 0.2);
        double z = playerZ + Mth.clamp(
            toClosest(playerZ, Math.floor(playerZ) + A, Math.floor(playerZ) + B) - playerZ,
            -0.2, 0.2);

        return new Vec3(x, mc.player.getY() - 0.5, z);
    }

    private double toClosest(double num, double min, double max) {
        return (num - min) > (max - num) ? max : min;
    }

    private float calcYaw(Vec3 target) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 diff = target.subtract(eye);
        return (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
    }
}
