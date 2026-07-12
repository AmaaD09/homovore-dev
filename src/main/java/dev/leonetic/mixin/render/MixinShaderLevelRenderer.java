package dev.leonetic.mixin.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.leonetic.Homovore;
import dev.leonetic.features.modules.render.ShadersModule;
import dev.leonetic.util.render.WorldChamsChain;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(LevelRenderer.class)
public class MixinShaderLevelRenderer {
    @Shadow
    private RenderTarget entityOutlineTarget;

    @Inject(method = "shouldShowEntityOutlines", at = @At("HEAD"), cancellable = true)
    private void homovore$forceOutlines(CallbackInfoReturnable<Boolean> cir) {
        ShadersModule shaders = Homovore.moduleManager == null
                ? null
                : Homovore.moduleManager.getModuleByClass(ShadersModule.class);
        if (shaders != null && shaders.isEnabled() && shaders.wantsOutlines()) {
            cir.setReturnValue(true);
        }
    }

    @Redirect(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ShaderManager;getPostChain(Lnet/minecraft/resources/Identifier;Ljava/util/Set;)Lnet/minecraft/client/renderer/PostChain;"
        )
    )
    private PostChain homovore$swapEntityOutlineChain(net.minecraft.client.renderer.ShaderManager sm,
                                                      Identifier id,
                                                      Set<Identifier> externalTargets) {
        ShadersModule shaders = Homovore.moduleManager == null
                ? null
                : Homovore.moduleManager.getModuleByClass(ShadersModule.class);
        if (shaders != null && shaders.isEnabled()
                && shaders.mode.getValue() == ShadersModule.Mode.Default) {

            PostChain optimised = WorldChamsChain.get(externalTargets,
                    shaders.getGlowRadius(), shaders.getGlowIntensity(),
                    shaders.getFillTint(), shaders.getFillAlpha(),
                    shaders.getInnerGlowRadius(), shaders.getInnerGlowIntensity());
            if (optimised != null) return optimised;
        }
        return sm.getPostChain(id, externalTargets);
    }
}
