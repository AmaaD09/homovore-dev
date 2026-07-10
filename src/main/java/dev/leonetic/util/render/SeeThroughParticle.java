package dev.leonetic.util.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;

public final class SeeThroughParticle {
    private SeeThroughParticle() {}
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
            .withLocation("homovore/see_through_translucent_particle")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .build();
    public static final SingleQuadParticle.Layer TRANSLUCENT_LAYER =
            new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, PIPELINE);
}
