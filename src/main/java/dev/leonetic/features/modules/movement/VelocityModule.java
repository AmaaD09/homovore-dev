package dev.leonetic.features.modules.movement;

import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import net.minecraft.client.Minecraft;

public class VelocityModule extends Module {
    public final Setting<Boolean> phaseOnly = bool("PhaseOnly", true);
    public final Setting<Boolean> knockback = bool("Knockback", true);
    public final Setting<Boolean> explosions = bool("Explosions", false);
    public final Setting<Boolean> entityPush = bool("EntityPush", true);
    public final Setting<Boolean> blockPush = bool("BlockPush", true);

    public VelocityModule() {
        super("Velocity", "Cancels knockback, explosions, and entity/block push.", Category.MOVEMENT);
    }

    public boolean shouldCancelKnockback() {
        return isEnabled() && knockback.getValue() && phaseConditionMet();
    }

    public boolean shouldCancelEntityPush() {
        return isEnabled() && entityPush.getValue();
    }

    public boolean phaseConditionMet() {
        if (!phaseOnly.getValue()) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        return mc.level.getBlockCollisions(mc.player, mc.player.getBoundingBox()).iterator().hasNext();
    }
}
