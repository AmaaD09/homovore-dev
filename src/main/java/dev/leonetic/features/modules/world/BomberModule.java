package dev.leonetic.features.modules.world;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.PreTickEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.manager.SwapRequest;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.ResultType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class BomberModule extends Module {
    private final Setting<Float>   radius      = num("Radius", 4.0f, 1.0f, 6.0f);
    private final Setting<Integer> delay       = num("Delay",  6,    0,    20);
    private final Setting<Boolean> autoDisable = bool("AutoDisable", false);

    private static final int IGNITE_PRIORITY = 40;

    private int      ticksWaited = 0;
    private BlockPos lastPos     = null;

    public BomberModule() {
        super("Bomber", "Automatically places and lights TNT around you.", Category.WORLD);
    }

    @Override
    public void onEnable() {
        ticksWaited = 0;
        lastPos     = null;
    }

    @Subscribe
    private void onPreTick(PreTickEvent event) {
        if (nullCheck()) return;

        if (ticksWaited < delay.getValue()) {
            ticksWaited++;
            return;
        }
        ticksWaited = 0;

        Result tnt = findHotbar(Items.TNT);
        Result fas = findHotbar(Items.FLINT_AND_STEEL);
        if (tnt == null || fas == null) {
            if (autoDisable.getValue()) disable();
            return;
        }

        placeAndIgnite(tnt, fas);
    }

    private void placeAndIgnite(Result tnt, Result fas) {
        BlockPos player = mc.player.blockPosition();
        double r = radius.getValue();

        for (double x = -r; x <= r; x++) {
            for (double z = -r; z <= r; z++) {
                if (x * x + z * z > r * r) continue;

                BlockPos pos = player.offset((int) x, 0, (int) z);
                if (pos.equals(lastPos)) continue;
                if (!mc.level.getBlockState(pos).isAir()) continue;

                lastPos = pos;

                if (!Homovore.placementManager.enqueue(pos, tnt.slot())) continue;
                Homovore.placementManager.flushQueue();

                igniteAt(pos, fas);
                return;
            }
        }
    }

    private void igniteAt(BlockPos pos, Result fas) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        Homovore.swapManager.submit(new SwapRequest("Bomber_ignite", IGNITE_PRIORITY, fas, () -> {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }));
    }

    private Result findHotbar(Item item) {
        Result r = InventoryUtil.find(item, InventoryUtil.PLACE_SCOPE);
        return r.found() ? r : null;
    }
}
