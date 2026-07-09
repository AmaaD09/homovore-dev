package dev.leonetic.features.modules.render;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.TotemPopEvent;
import dev.leonetic.event.impl.network.DisconnectEvent;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.render.WireframeEntityRenderer;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PopEffectsModule extends Module {

    public final Setting<Color> primaryColor =
            color("Primary", 0xE8, 0xFF, 0x55, 0xFF).setPage("Particles");
    public final Setting<Color> accentColor =
            color("Accent", 0x55, 0xFF, 0x55, 0xFF).setPage("Particles");
    public final Setting<Float> scale =
            num("Scale", 1.0f, 0.1f, 5.0f).setPage("Particles");

    public final Setting<Boolean> ghosts =
            bool("Ghosts", true).setPage("Ghosts");
    public final Setting<Float> fadeTime =
            num("FadeTime", 2.0f, 0.1f, 15.0f).setPage("Ghosts");
    public final Setting<Color> ghostColor =
            color("GhostColor", 0xE8, 0xFF, 0x55, 0xFF).setPage("Ghosts");
    public final Setting<Color> ghostSideColor =
            color("GhostSideColor", 0xE8, 0xFF, 0x55, 0x37).setPage("Ghosts");
    public final Setting<Float> ghostLineWidth =
            num("GhostLineWidth", 1.5f, 0.5f, 5.0f).setPage("Ghosts");
    public final Setting<Boolean> move =
            bool("Move", false).setPage("Ghosts");
    public final Setting<Float> moveSpeed =
            num("MoveSpeed", 1.0f, 0.05f, 10.0f).setPage("Ghosts");

    private final List<Ghost> ghostList = new CopyOnWriteArrayList<>();

    public PopEffectsModule() {
        super("PopEffects", "Customize totem pop particle effects", Category.RENDER);
        fadeTime.setVisibility(v -> ghosts.getValue());
        ghostColor.setVisibility(v -> ghosts.getValue());
        ghostSideColor.setVisibility(v -> ghosts.getValue());
        ghostLineWidth.setVisibility(v -> ghosts.getValue());
        move.setVisibility(v -> ghosts.getValue());
        moveSpeed.setVisibility(v -> ghosts.getValue() && move.getValue());
    }

    public static PopEffectsModule get() {
        if (Homovore.moduleManager == null) return null;
        return Homovore.moduleManager.getModuleByClass(PopEffectsModule.class);
    }

    public boolean shouldCustomize(ParticleType<?> type) {
        return isEnabled() && type == ParticleTypes.TOTEM_OF_UNDYING;
    }

    public int getRgb(boolean yellowVariant) {
        Color c = yellowVariant ? primaryColor.getValue() : accentColor.getValue();
        return (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();
    }

    public float getScale() {
        return Math.max(0.1f, scale.getValue());
    }

    @Override
    public void onEnable() {
        ghostList.clear();
    }

    @Override
    public void onDisable() {
        ghostList.clear();
    }

    @Subscribe
    public void onTotemPop(TotemPopEvent event) {
        if (!ghosts.getValue() || nullCheck()) return;

        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;
        if (player == mc.player) return;

        ghostList.add(new Ghost(player, mc.level.dimension()));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        ghostList.clear();
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (nullCheck() || ghostList.isEmpty()) return;

        if (!ghosts.getValue()) {
            ghostList.clear();
            return;
        }

        ResourceKey<Level> dim = mc.level.dimension();
        long fadeMillis = (long) (fadeTime.getValue() * 1000.0f);
        long now = System.currentTimeMillis();

        for (Ghost ghost : ghostList) {
            long elapsed = now - ghost.popTime;
            if (elapsed >= fadeMillis) {
                ghostList.remove(ghost);
                continue;
            }
            if (!dim.equals(ghost.dimension)) continue;

            float fade = 1.0f - (float) elapsed / fadeMillis;
            Vec3 pos = move.getValue()
                    ? ghost.pos.add(0.0, elapsed / 1000.0 * moveSpeed.getValue(), 0.0)
                    : ghost.pos;

            ghost.capturedGeometry = WireframeEntityRenderer.render(
                    event.getMatrix(),
                    ghost.entity,
                    pos,
                    ghost.capturedGeometry,
                    event.getDelta(),
                    withFade(ghostSideColor.getValue(), fade),
                    withFade(ghostColor.getValue(), fade),
                    ghostLineWidth.getValue()
            );
        }
    }

    private static Color withFade(Color color, float fade) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.round(color.getAlpha() * fade));
    }

    private static class Ghost {
        final Player entity;
        final Vec3 pos;
        final ResourceKey<Level> dimension;
        final long popTime;

        List<float[][]> capturedGeometry = null;

        Ghost(Player entity, ResourceKey<Level> dimension) {
            this.entity    = entity;
            this.pos       = entity.position();
            this.dimension = dimension;
            this.popTime   = System.currentTimeMillis();
        }
    }
}
