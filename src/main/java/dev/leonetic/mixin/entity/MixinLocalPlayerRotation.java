package dev.leonetic.mixin.entity;

import dev.leonetic.Homovore;
import dev.leonetic.manager.RotationManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static dev.leonetic.util.traits.Util.mc;

@Mixin(value = LocalPlayer.class, priority = Integer.MAX_VALUE)
public class MixinLocalPlayerRotation {
    @Shadow private float xRotLast;

    @Unique private float homovore$savedYaw, homovore$savedPitch;
    @Unique private boolean homovore$spoofed;

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void homovore$spoofRotationHead(CallbackInfo ci) {
        homovore$spoofed = false;
        RotationManager rm = Homovore.rotationManager;
        if (rm == null) return;

        boolean motion = rm.isRotating();
        boolean silent = rm.isSilentSyncRequired();
        if (!motion && !silent) return;

        homovore$savedYaw = mc.player.getYRot();
        homovore$savedPitch = mc.player.getXRot();

        float outYaw = homovore$savedYaw;
        float outPitch = homovore$savedPitch;

        if (motion) {
            outYaw = rm.getRotationYaw();
            outPitch = rm.getRotationPitch();
            rm.setServerDeltaYaw(outYaw - rm.getServerYaw());
            rm.setServerYaw(outYaw);
            rm.setServerPitch(outPitch);
        } else {
            xRotLast -= 4;
            float f = (float) ((Math.random() * 2.0 - 1.0) * 0.001f);
            outPitch = Mth.clamp(outPitch + f, -90.0f, 90.0f);
        }

        mc.player.setYRot(outYaw);
        mc.player.setXRot(outPitch);
        homovore$spoofed = true;
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void homovore$spoofRotationTail(CallbackInfo ci) {
        RotationManager rm = Homovore.rotationManager;
        if (rm == null) return;

        if (rm.isRotating()) {
            rm.setServerYaw(mc.player.getYRot());
            rm.setServerPitch(mc.player.getXRot());
        }

        if (homovore$spoofed) {
            mc.player.setYRot(homovore$savedYaw);
            mc.player.setXRot(homovore$savedPitch);
            homovore$spoofed = false;
        }

        rm.setSilentSyncRequired(false);
        rm.resetSilentTick();
    }
}
