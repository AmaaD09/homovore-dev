package dev.leonetic.features.modules.funny;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.manager.RotationRequest;
import dev.leonetic.util.MathUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class EndermanLookModule extends Module {

    private static final String ROTATION_ID = "EndermanLook";
    private static final int ROTATION_PRIORITY = 1;

    private final Setting<Mode> lookMode = mode("LookMode", Mode.AWAY);
    private final Setting<Boolean> stunHostiles = bool("StunHostiles", true)
        .setVisibility(v -> lookMode.getValue() == Mode.AWAY);

    public EndermanLookModule() {
        super("EndermanLook", "Either stares at every Enderman or stops you from staring at them.", Category.FUNNY);
    }

    @Override
    public void onDisable() {
        if (Homovore.rotationManager != null) Homovore.rotationManager.cancel(ROTATION_ID);
    }

    @Override
    public void onTick() {
        if (nullCheck()) {
            cancel();
            return;
        }

        LocalPlayer player = mc.player;

        if (player.getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN) || player.getAbilities().instabuild) {
            cancel();
            return;
        }

        EnderMan stareTarget = null;
        EnderMan awayThreat = null;
        double bestStare = Double.MAX_VALUE;
        double bestAway = Double.MAX_VALUE;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EnderMan enderman) || !enderman.isAlive() || !player.hasLineOfSight(enderman)) {
                continue;
            }

            double dist = player.distanceToSqr(enderman);

            switch (lookMode.getValue()) {
                case AWAY -> {
                    if (enderman.isCreepy() && stunHostiles.getValue()) {
                        if (dist < bestStare) {
                            bestStare = dist;
                            stareTarget = enderman;
                        }
                    } else if (angleCheck(enderman) && dist < bestAway) {
                        bestAway = dist;
                        awayThreat = enderman;
                    }
                }
                case AT -> {
                    if (!enderman.isCreepy() && dist < bestStare) {
                        bestStare = dist;
                        stareTarget = enderman;
                    }
                }
            }
        }

        if (stareTarget != null) {
            float[] angles = MathUtil.calcAngle(player.getEyePosition(), stareTarget.getEyePosition());
            submit(angles[0], angles[1]);
        } else if (awayThreat != null) {
            submit(player.getYRot(), 90f);
        } else {
            cancel();
        }
    }

    private void submit(float yaw, float pitch) {
        Homovore.rotationManager.submit(new RotationRequest(
            ROTATION_ID, ROTATION_PRIORITY, yaw, pitch, RotationRequest.Mode.MOTION, true));
    }

    private void cancel() {
        if (Homovore.rotationManager != null) Homovore.rotationManager.cancel(ROTATION_ID);
    }

    private boolean angleCheck(EnderMan enderman) {
        Vec3 view = mc.player.getViewVector(1.0f).normalize();
        Vec3 diff = new Vec3(
            enderman.getX() - mc.player.getX(),
            enderman.getEyeY() - mc.player.getEyeY(),
            enderman.getZ() - mc.player.getZ()
        );

        double d = diff.length();
        double dot = view.dot(diff.normalize());

        return dot > 1.0 - 0.025 / d;
    }

    public enum Mode {
        AT,
        AWAY
    }
}
