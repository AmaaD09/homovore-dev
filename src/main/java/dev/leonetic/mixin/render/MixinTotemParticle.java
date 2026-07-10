package dev.leonetic.mixin.render;

import dev.leonetic.features.modules.render.PopEffectsModule;
import dev.leonetic.util.render.SeeThroughParticle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TotemParticle;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TotemParticle.class)
public abstract class MixinTotemParticle extends SimpleAnimatedParticle {

    protected MixinTotemParticle(ClientLevel level, double x, double y, double z,
                                 SpriteSet sprites, float scale) {
        super(level, x, y, z, sprites, scale);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void homovore$customizeTotem(ClientLevel level, double x, double y, double z,
                                         double xSpeed, double ySpeed, double zSpeed,
                                         SpriteSet sprites, CallbackInfo ci) {
        PopEffectsModule module = PopEffectsModule.get();
        if (module == null || !module.shouldCustomize(ParticleTypes.TOTEM_OF_UNDYING)) return;

        boolean yellowVariant = this.rCol >= 0.5f && this.gCol >= 0.5f;

        this.setColor(module.getRgb(yellowVariant));
        this.quadSize *= module.getScale();
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        PopEffectsModule module = PopEffectsModule.get();
        if (module != null
                && module.shouldCustomize(ParticleTypes.TOTEM_OF_UNDYING)
                && module.throughWalls.getValue()) {
            return SeeThroughParticle.TRANSLUCENT_LAYER;
        }
        return super.getLayer();
    }
}
