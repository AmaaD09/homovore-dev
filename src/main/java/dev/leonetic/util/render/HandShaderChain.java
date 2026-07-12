package dev.leonetic.util.render;

import dev.leonetic.Homovore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class HandShaderChain {

    private static final Identifier SCREENQUAD        = Identifier.fromNamespaceAndPath("minecraft", "core/screenquad");
    private static final Identifier DILATE_H_FSH      = Identifier.fromNamespaceAndPath("homovore", "post/hand_outline_h");
    private static final Identifier OUTLINE_FSH       = Identifier.fromNamespaceAndPath("homovore", "post/hand_outline");
    private static final Identifier GLOW_H_FSH        = Identifier.fromNamespaceAndPath("homovore", "post/hand_glow_h");
    private static final Identifier INNER_GLOW_H_FSH  = Identifier.fromNamespaceAndPath("homovore", "post/hand_inner_glow_h");
    private static final Identifier OUTLINE_GLOW_FSH  = Identifier.fromNamespaceAndPath("homovore", "post/hand_outline_glow");
    private static final Identifier DILATED           = Identifier.fromNamespaceAndPath("homovore", "hand_dilated");
    private static final Identifier GLOW_H            = Identifier.fromNamespaceAndPath("homovore", "hand_glow_h");
    private static final Identifier INNER_GLOW_H      = Identifier.fromNamespaceAndPath("homovore", "hand_inner_glow_h");
    private static final Identifier CHAIN_NAME        = Identifier.fromNamespaceAndPath("homovore", "hand_shader_runtime");
    private static final UniformWriter UNIFORM_WRITER = new UniformWriter();

    private static final float FILL_ALPHA = 0.35f;

    private static CachedOrthoProjectionMatrixBuffer projection;
    private static PostChain cached;
    private static int   lastLineWidth          = Integer.MIN_VALUE;
    private static boolean lastFill             = false;
    private static int   lastGlowRadius         = Integer.MIN_VALUE;
    private static float lastGlowIntensity      = Float.NaN;
    private static int   lastInnerGlowRadius    = Integer.MIN_VALUE;
    private static float lastInnerGlowIntensity = Float.NaN;

    private HandShaderChain() {}

    public static PostChain get(boolean outline, int thickness, boolean fill,
                                boolean glow, int glowRadius, float glowIntensity,
                                boolean innerGlow, int innerGlowRadius, float innerGlowIntensity) {
        int lineWidth = outline ? Math.max(1, thickness) : 0;
        int glowR = glow ? Math.max(0, glowRadius) : 0;
        int innerR = innerGlow ? Math.max(0, innerGlowRadius) : 0;
        if (lineWidth == 0 && !fill && glowR == 0 && innerR == 0) return null;

        if (cached != null && lineWidth == lastLineWidth && fill == lastFill
                && glowR == lastGlowRadius && glowIntensity == lastGlowIntensity
                && innerR == lastInnerGlowRadius && innerGlowIntensity == lastInnerGlowIntensity) {
            return cached;
        }
        else if (cached != null && lastGlowRadius == glowR && lastInnerGlowRadius == innerR) // if glow was just toggled we need to recreate the postchain.
        {
            lastLineWidth = lineWidth;
            lastFill = fill;
            lastGlowRadius = glowR;
            lastGlowIntensity = glowIntensity;
            lastInnerGlowRadius = innerR;
            lastInnerGlowIntensity = innerGlowIntensity;

            float fillA = fill ? FILL_ALPHA : 0.0f;

            Map<String, List<UniformValue>> configs = new HashMap<>();
            List<UniformValue> dilateConfig = List.of(integer(lineWidth));
            List<UniformValue> outlineConfig;
            List<UniformValue> glowConfig = List.of(integer(glowR));
            List<UniformValue> innerGlowConfig = List.of(integer(innerR));
            if (glowR > 0 || innerR > 0)
            {
                outlineConfig = List.of(
                        flt(fillA),
                        flt(1.0f),
                        flt(glowIntensity),
                        integer(lineWidth),
                        integer(glowR),
                        flt(innerGlowIntensity),
                        integer(innerR));
            }
            else
            {
                outlineConfig = List.of(flt(fillA), flt(1.0f), integer(lineWidth));
            }

            configs.put("DilateConfig", dilateConfig);
            configs.put("OutlineConfig", outlineConfig);
            configs.put("GlowConfig", glowConfig);
            configs.put("InnerGlowConfig", innerGlowConfig);
            UNIFORM_WRITER.setUniforms(cached, configs);
            return cached;
        }

        PostChain rebuilt = build(lineWidth, fill, glowR, glowIntensity, innerR, innerGlowIntensity);
        if (rebuilt == null) return cached;
        if (cached != null) cached.close();
        cached = rebuilt;
        lastLineWidth = lineWidth;
        lastFill = fill;
        lastGlowRadius = glowR;
        lastGlowIntensity = glowIntensity;
        lastInnerGlowRadius = innerR;
        lastInnerGlowIntensity = innerGlowIntensity;
        return cached;
    }

    private static PostChain build(int lineWidth, boolean fill, int glowRadius, float glowIntensity,
                                   int innerGlowRadius, float innerGlowIntensity) {
        try {
            if (projection == null) {
                projection = new CachedOrthoProjectionMatrixBuffer("homovore_hand", 0.1f, 1000.0f, false);
            }

            float fillA = fill ? FILL_ALPHA : 0.0f;

            PostChainConfig.Pass dilateH = new PostChainConfig.Pass(
                    SCREENQUAD, DILATE_H_FSH,
                    List.of(new PostChainConfig.TargetInput("In", PostChain.MAIN_TARGET_ID, false, false)),
                    DILATED,
                    Map.of("DilateConfig", List.<UniformValue>of(integer(lineWidth))));

            PostChainConfig config = (glowRadius > 0 || innerGlowRadius > 0)
                    ? buildGlow(dilateH, fillA, lineWidth, glowRadius, glowIntensity, innerGlowRadius, innerGlowIntensity)
                    : buildPlain(dilateH, fillA, lineWidth);

            return PostChain.load(config, Minecraft.getInstance().getTextureManager(),
                    LevelTargetBundle.MAIN_TARGETS, CHAIN_NAME, projection);
        } catch (Exception e) {
            return null;
        }
    }

    private static PostChainConfig buildPlain(PostChainConfig.Pass dilateH, float fillA, int lineWidth) {

        List<UniformValue> outlineConfig = List.of(
                flt(fillA),
                flt(1.0f),
                integer(lineWidth)
        );
        PostChainConfig.Pass outline = new PostChainConfig.Pass(
                SCREENQUAD, OUTLINE_FSH,
                List.of(new PostChainConfig.TargetInput("In", DILATED, false, false),
                        new PostChainConfig.TargetInput("Orig", PostChain.MAIN_TARGET_ID, false, false)),
                PostChain.MAIN_TARGET_ID,
                Map.of("OutlineConfig", outlineConfig));

        return new PostChainConfig(
                Map.of(DILATED, new PostChainConfig.InternalTarget(Optional.empty(), Optional.empty(), false, 0)),
                List.of(dilateH, outline));
    }

    private static PostChainConfig buildGlow(PostChainConfig.Pass dilateH, float fillA,
                                             int lineWidth, int glowRadius, float glowIntensity,
                                             int innerGlowRadius, float innerGlowIntensity) {

        PostChainConfig.Pass glowH = new PostChainConfig.Pass(
                SCREENQUAD, GLOW_H_FSH,
                List.of(new PostChainConfig.TargetInput("In", PostChain.MAIN_TARGET_ID, false, false)),
                GLOW_H,
                Map.of("GlowConfig", List.<UniformValue>of(integer(glowRadius))));

        PostChainConfig.Pass innerGlowH = new PostChainConfig.Pass(
                SCREENQUAD, INNER_GLOW_H_FSH,
                List.of(new PostChainConfig.TargetInput("In", PostChain.MAIN_TARGET_ID, false, false)),
                INNER_GLOW_H,
                Map.of("InnerGlowConfig", List.<UniformValue>of(integer(innerGlowRadius))));

        List<UniformValue> outlineConfig = List.of(
                flt(fillA),
                flt(1.0f),
                flt(glowIntensity),
                integer(lineWidth),
                integer(glowRadius),
                flt(innerGlowIntensity),
                integer(innerGlowRadius)
        );
        PostChainConfig.Pass outline = new PostChainConfig.Pass(
                SCREENQUAD, OUTLINE_GLOW_FSH,
                List.of(new PostChainConfig.TargetInput("In", DILATED, false, false),
                        new PostChainConfig.TargetInput("Glow", GLOW_H, false, false),
                        new PostChainConfig.TargetInput("InnerGlow", INNER_GLOW_H, false, false),
                        new PostChainConfig.TargetInput("Orig", PostChain.MAIN_TARGET_ID, false, false)),
                PostChain.MAIN_TARGET_ID,
                Map.of("OutlineConfig", outlineConfig));

        return new PostChainConfig(
                Map.of(DILATED,       new PostChainConfig.InternalTarget(Optional.empty(), Optional.empty(), false, 0),
                       GLOW_H,        new PostChainConfig.InternalTarget(Optional.empty(), Optional.empty(), false, 0),
                       INNER_GLOW_H,  new PostChainConfig.InternalTarget(Optional.empty(), Optional.empty(), false, 0)),
                List.of(dilateH, glowH, innerGlowH, outline));
    }

    private static UniformValue flt(float v) {
        return new UniformValue.FloatUniform(v);
    }

    private static UniformValue integer(int v) {
        return new UniformValue.IntUniform(v);
    }
}
