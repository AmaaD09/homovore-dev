package dev.leonetic.features.modules.render;

import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.render.RenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

/**
 * Renders the block grid around the player's feet as a circular, holographic field.
 * Each block column is sampled for its surface height, so the grid drapes over the
 * terrain at different levels — climbing blocks and dipping into holes around you.
 * Solid in the middle, fading out toward the edge, with an animated ripple.
 */
public class QuadSupremeModule extends Module {

    public Setting<Integer> radius     = num("Radius", 5, 2, 16);
    public Setting<Integer> depth      = num("Depth", 3, 1, 8);
    public Setting<Float>   yOffset    = num("Height", 0.02f, 0f, 1f);
    public Setting<Float>   lineWidth  = num("LineWidth", 1.5f, 0.5f, 5f);
    public Setting<Color>   color      = color("Color", 0, 200, 255, 200);
    public Setting<Float>   fadeStart  = num("FadeStart", 0.25f, 0f, 1f);
    public Setting<Boolean> pulse      = bool("Pulse", true);
    public Setting<Float>   pulseSpeed = num("PulseSpeed", 3f, 0f, 12f);

    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final Set<Long> drawnEdges = new HashSet<>();

    public QuadSupremeModule() {
        super("QuadSupreme", "Holographic block grid that drapes over the terrain at your feet.", Category.RENDER);
        pulseSpeed.setVisibility(v -> pulse.getValue());
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;

        float partial = event.getDelta();
        double cx = Mth.lerp(partial, mc.player.xOld, mc.player.getX());
        double cz = Mth.lerp(partial, mc.player.zOld, mc.player.getZ());
        int feetY = Mth.floor(Mth.lerp(partial, mc.player.yOld, mc.player.getY()));

        int r = radius.getValue();
        double rSq = (double) r * r;
        Color base = color.getValue();
        float width = lineWidth.getValue();
        float offset = yOffset.getValue();
        double phase = pulse.getValue()
                ? (System.currentTimeMillis() % 100000L) / 1000.0 * pulseSpeed.getValue()
                : 0;

        drawnEdges.clear();

        int minX = Mth.floor(cx) - r;
        int maxX = Mth.floor(cx) + r;
        int minZ = Mth.floor(cz) - r;
        int maxZ = Mth.floor(cz) + r;

        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                // keep the field circular — test the cell centre against the radius
                double ddx = bx + 0.5 - cx;
                double ddz = bz + 0.5 - cz;
                if (ddx * ddx + ddz * ddz > rSq) continue;

                int top = surfaceTop(bx, bz, feetY);
                if (top == Integer.MIN_VALUE) continue; // no surface (deep hole / void) -> gap

                float y = top + offset;

                // top-face outline of this block cell; shared edges are drawn once
                edge(bx,     bz,     bx + 1, bz,     y, top, cx, cz, r, base, width, phase); // south
                edge(bx,     bz + 1, bx + 1, bz + 1, y, top, cx, cz, r, base, width, phase); // north
                edge(bx,     bz,     bx,     bz + 1, y, top, cx, cz, r, base, width, phase); // west
                edge(bx + 1, bz,     bx + 1, bz + 1, y, top, cx, cz, r, base, width, phase); // east
            }
        }
    }

    /**
     * Highest solid block with a passable block above it, within {@code depth} blocks
     * of the player's feet. Returns the world Y of that block's top face, or
     * {@link Integer#MIN_VALUE} if the column has no surface in range.
     */
    private int surfaceTop(int bx, int bz, int feetY) {
        int range = depth.getValue();
        for (int y = feetY + range; y >= feetY - range; y--) {
            if (isSolid(bx, y, bz) && !isSolid(bx, y + 1, bz)) return y + 1;
        }
        return Integer.MIN_VALUE;
    }

    private boolean isSolid(int x, int y, int z) {
        cursor.set(x, y, z);
        return !mc.level.getBlockState(cursor).getCollisionShape(mc.level, cursor).isEmpty();
    }

    /** Draws one unit grid edge (deduped), tinted by its distance from the centre. */
    private void edge(int x1, int z1, int x2, int z2, float y, int top,
                      double cx, double cz, int r, Color base, float width, double phase) {
        if (!drawnEdges.add(edgeKey(x1, z1, x2, z2, top))) return;

        double midX = (x1 + x2) * 0.5;
        double midZ = (z1 + z2) * 0.5;
        double dist = Math.sqrt((midX - cx) * (midX - cx) + (midZ - cz) * (midZ - cz));

        Color c = shade(base, dist, r, phase);
        if (c.getAlpha() <= 2) return;

        RenderUtil.drawLine(new Vec3(x1, y, z1), new Vec3(x2, y, z2), c, width);
    }

    /** Canonical key for a unit edge so cells sharing an edge at the same height don't double-draw. */
    private static long edgeKey(int x1, int z1, int x2, int z2, int y) {
        int ax = Math.min(x1, x2), az = Math.min(z1, z2);
        int bx = Math.max(x1, x2), bz = Math.max(z1, z2);
        long k = (ax & 0xFFFFFL);
        k = k * 0x100000L + (az & 0xFFFFFL);
        k = k * 0x2L + (ax == bx ? 0 : 1);   // orientation (only one of x/z differs)
        k = k * 0x100000L + (y & 0xFFFFFL);
        return k;
    }

    /** Radial fade (solid centre -> transparent edge) plus an optional outward ripple. */
    private Color shade(Color base, double dist, int r, double phase) {
        float t = (float) (dist / r);
        if (t > 1f) t = 1f;

        float start = fadeStart.getValue();
        float fade = t <= start ? 1f : 1f - (t - start) / (1f - start);
        fade = Mth.clamp(fade, 0f, 1f);
        fade = fade * fade * (3f - 2f * fade); // smoothstep for a softer falloff

        if (pulse.getValue()) {
            float ripple = 0.65f + 0.35f * (float) Math.sin(phase - dist * 1.2);
            fade *= ripple;
        }

        int alpha = Mth.clamp(Math.round(base.getAlpha() * fade), 0, 255);
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
    }
}
