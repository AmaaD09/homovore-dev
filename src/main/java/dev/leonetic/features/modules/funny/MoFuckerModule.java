package dev.leonetic.features.modules.funny;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.TickEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.manager.RotationRequest;
import dev.leonetic.manager.SwapRequest;
import dev.leonetic.util.MathUtil;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import static dev.leonetic.util.inventory.InventoryUtil.FULL_SCOPE;

public class MoFuckerModule extends Module {

    private final Setting<Float>   range = num("Range", 5.0f, 1.0f, 6.0f);
    private final Setting<Integer> delay = num("Delay", 2, 0, 20);

    private static final int PRIORITY = 100;

    private State    state;
    private BlockPos targetPos;
    private int      ticks;

    public MoFuckerModule() {
        super("MoFucker", "On keybind, dumps a water bucket on the nearest enemy and scoops it back up.", Category.FUNNY);
    }

    @Override
    public void onEnable() {
        state     = State.PLACE;
        targetPos = null;
        ticks     = 0;
    }

    @Subscribe
    private void onTick(TickEvent event) {
        if (nullCheck() || mc.screen != null) { disable(); return; }

        switch (state) {
            case PLACE   -> place();
            case COLLECT -> collect();
        }
    }

    private void place() {
        Player target = findTarget();
        if (target == null) { disable(); return; }

        Result water = InventoryUtil.find(Items.WATER_BUCKET, FULL_SCOPE);
        if (!water.found()) { disable(); return; }

        targetPos = target.blockPosition();
        // Aim at the top face of the block under the feet: the ray hits it pointing
        // UP, so the water empties into the feet block itself, not an adjacent cell.
        useAt(Vec3.atBottomCenterOf(targetPos), water, "MoFucker_place");

        state = State.COLLECT;
        ticks = 0;
    }

    private void collect() {
        if (ticks++ < delay.getValue()) return;

        Result bucket = InventoryUtil.find(Items.BUCKET, FULL_SCOPE);
        if (!bucket.found()) { disable(); return; }

        // Scoop the source back out of the feet block.
        useAt(Vec3.atCenterOf(targetPos), bucket, "MoFucker_collect");

        disable();
    }

    private void useAt(Vec3 aim, Result item, String id) {
        float[] angles = MathUtil.calcAngle(mc.player.getEyePosition(1.0f), aim);

        // Keep the server view in sync with where we aim.
        Homovore.rotationManager.submit(new RotationRequest(
                "MoFucker", PRIORITY, angles[0], angles[1], RotationRequest.Mode.SILENT));

        Homovore.swapManager.submit(new SwapRequest(id, 40, item, () -> {
            // useItem's bucket raycast uses the client's real rotation, so aim it
            // at the target for the duration of the interaction, then restore.
            float prevYaw   = mc.player.getYRot();
            float prevPitch = mc.player.getXRot();
            mc.player.setYRot(angles[0]);
            mc.player.setXRot(angles[1]);
            try {
                mc.gameMode.useItem(mc.player, item.hand());
            } finally {
                mc.player.setYRot(prevYaw);
                mc.player.setXRot(prevPitch);
            }
        }));
    }

    private Player findTarget() {
        double reachSq = range.getValue() * range.getValue();
        Player best = null;
        double bestSq = Double.MAX_VALUE;

        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            if (Homovore.friendManager.isFriend(p)) continue;

            double distSq = mc.player.distanceToSqr(p);
            if (distSq > reachSq) continue;
            if (distSq < bestSq) {
                bestSq = distSq;
                best   = p;
            }
        }
        return best;
    }

    private enum State { PLACE, COLLECT }
}
