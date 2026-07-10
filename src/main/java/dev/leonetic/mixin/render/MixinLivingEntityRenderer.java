package dev.leonetic.mixin.render;

import dev.leonetic.Homovore;
import dev.leonetic.manager.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
        at = @At("TAIL")
    )
    private void homovore$renderMotionRotation(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
        if (entity != Minecraft.getInstance().player) return;

        RotationManager rm = Homovore.rotationManager;
        if (rm == null || !rm.isRotating()) return;

        state.bodyRot = rm.getRenderYaw(partialTicks);
        state.yRot = 0f;
        state.xRot = rm.getRenderPitch(partialTicks);
    }
}
