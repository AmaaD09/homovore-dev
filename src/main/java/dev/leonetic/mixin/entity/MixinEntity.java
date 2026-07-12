package dev.leonetic.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.leonetic.Homovore;
import dev.leonetic.features.modules.movement.VelocityModule;
import dev.leonetic.features.modules.render.ShadersModule;
import dev.leonetic.manager.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class MixinEntity {

    @Inject(method = "getLookAngle", at = @At("HEAD"), cancellable = true)
    private void homovore$spoofLookAngle(CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity) (Object) this;
        if (self != Minecraft.getInstance().player) return;

        RotationManager rm = Homovore.rotationManager;
        if (rm == null || !rm.isMoveFixEnabled() || !rm.isRotating()) return;

        cir.setReturnValue(self.calculateViewVector(rm.getRotationPitch(), rm.getRotationYaw()));
    }
    @Inject(method = "isCurrentlyGlowing", at = @At("RETURN"), cancellable = true)
    private void onIsCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;

        ShadersModule shaders = Homovore.moduleManager.getModuleByClass(ShadersModule.class);
        if (shaders != null && shaders.isEnabled() && shaders.shouldShader(self)) {
            cir.setReturnValue(true);
            return;
        }

    }

    @Inject(method = "getTeamColor", at = @At("RETURN"), cancellable = true)
    private void onGetTeamColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;

        ShadersModule shaders = Homovore.moduleManager.getModuleByClass(ShadersModule.class);
        if (shaders != null && shaders.isEnabled() && shaders.shouldShader(self)) {
            cir.setReturnValue(shaders.getRgbFor(self));
            return;
        }

    }

    @WrapOperation(
        method = "push(Lnet/minecraft/world/entity/Entity;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;push(DDD)V")
    )
    private void cancelEntityPush(Entity pushed, double x, double y, double z, Operation<Void> original) {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        VelocityModule velocity = Homovore.moduleManager.getModuleByClass(VelocityModule.class);
        if (localPlayer != null && pushed == localPlayer && velocity != null && velocity.shouldCancelEntityPush()) return;

        original.call(pushed, x, y, z);
    }
}
