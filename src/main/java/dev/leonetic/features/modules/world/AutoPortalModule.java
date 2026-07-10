package dev.leonetic.features.modules.world;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.PreTickEvent;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.manager.SwapRequest;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.ResultType;
import dev.leonetic.util.player.ChatUtil;
import dev.leonetic.util.render.RenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AutoPortalModule extends Module {
    private static final int IGNITE_PRIORITY = 40;

    private final Setting<Boolean> render       = bool("Render", true);
    private final Setting<Float>   fadeTime     = num("FadeTime", 1.0f, 0.05f, 2.0f);
    private final Setting<Color>   fillColor    = color("FillColor", 85, 0, 255, 44);
    private final Setting<Color>   outlineColor = color("OutlineColor", 85, 0, 255, 44);

    private final List<BlockPos> portalBlocks = new ArrayList<>();
    private final Map<BlockPos, Long> renderMap = new HashMap<>();

    public AutoPortalModule() {
        super("AutoPortal", "For the base hunter who has places to be.", Category.WORLD);
    }

    @Override
    public void onEnable() {
        portalBlocks.clear();

        int obsidianCount = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == Items.OBSIDIAN) {
                obsidianCount += mc.player.getInventory().getItem(i).getCount();
            }
        }
        if (obsidianCount < 10) {
            ChatUtil.sendMessage(Component.literal("[AutoPortal] Not enough obsidian (need at least 10)!"));
            disable();
            return;
        }

        Direction forward    = mc.player.getDirection();
        Direction right      = forward.getClockWise();
        BlockPos standingPos = mc.player.blockPosition();
        BlockPos blockBelow  = standingPos.below();

        double blockHeight;
        try {
            blockHeight = mc.level.getBlockState(blockBelow)
                .getCollisionShape(mc.level, blockBelow).max(Direction.Axis.Y);
        } catch (Exception e) {
            blockHeight = 1.0;
        }
        if (blockHeight < 1.0) standingPos = standingPos.above();

        BlockPos base = standingPos.relative(forward, 2).relative(right, -1);

        List<BlockPos> frame = new ArrayList<>();
        frame.add(base.relative(right, 1));
        frame.add(base.relative(right, 2));
        for (int i = 1; i <= 3; i++) frame.add(base.relative(right, 0).above(i));
        for (int i = 1; i <= 3; i++) frame.add(base.relative(right, 3).above(i));
        frame.add(base.relative(right, 1).above(4));
        frame.add(base.relative(right, 2).above(4));

        long obsidianMatches = frame.stream()
            .filter(pos -> mc.level.getBlockState(pos).getBlock().asItem() == Items.OBSIDIAN)
            .count();
        if (obsidianMatches >= frame.size()) {
            ChatUtil.sendMessage(Component.literal("[AutoPortal] A portal already exists here!"));
            disable();
            return;
        }

        boolean obstructed = frame.stream().anyMatch(pos ->
            !mc.level.getBlockState(pos).canBeReplaced()
                && mc.level.getBlockState(pos).getBlock().asItem() != Items.OBSIDIAN);
        if (obstructed) {
            ChatUtil.sendMessage(Component.literal("[AutoPortal] Portal area obstructed. Move and try again."));
            disable();
            return;
        }

        portalBlocks.addAll(frame);
    }

    @Override
    public void onDisable() {
        Homovore.placementManager.removeQueuedFor(portalBlocks::contains);
        portalBlocks.clear();
        renderMap.clear();
    }

    @Subscribe
    private void onPreTick(PreTickEvent event) {
        if (nullCheck() || portalBlocks.isEmpty()) return;

        boolean allDone = true;
        for (BlockPos pos : portalBlocks) {
            if (mc.level.getBlockState(pos).getBlock().asItem() != Items.OBSIDIAN) {
                allDone = false;
                break;
            }
        }
        if (allDone) {
            ignite();
            ChatUtil.sendMessage(Component.literal("[AutoPortal] Portal complete!"));
            disable();
            return;
        }

        Result obsidian = findHotbar(Items.OBSIDIAN);
        if (obsidian == null) return;

        Vec3 eye = mc.player.getEyePosition();
        long now = System.currentTimeMillis();
        for (BlockPos pos : portalBlocks) {
            var state = mc.level.getBlockState(pos);
            if (state.getBlock().asItem() == Items.OBSIDIAN) continue;
            if (Vec3.atCenterOf(pos).distanceTo(eye) > 5.154) {
                ChatUtil.sendMessage(Component.literal("[AutoPortal] Out of range — disabling."));
                disable();
                return;
            }
            if (state.canBeReplaced()) {
                if (Homovore.placementManager.enqueue(pos, obsidian.slot())) {
                    renderMap.put(pos, now);
                }
            } else {
                mc.gameMode.startDestroyBlock(pos, Direction.UP);
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
        }

        long fadeMs = (long) (fadeTime.getValue() * 1000);
        renderMap.entrySet().removeIf(e -> now - e.getValue() > fadeMs);
    }

    private void ignite() {
        Result fas = findHotbar(Items.FLINT_AND_STEEL);
        if (fas == null) return;

        BlockPos firePos = portalBlocks.get(0).above();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(firePos), Direction.UP, firePos, false);
        Homovore.swapManager.submit(new SwapRequest("AutoPortal_ignite", IGNITE_PRIORITY, fas, () -> {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }));
    }

    private Result findHotbar(net.minecraft.world.item.Item item) {
        Result r = InventoryUtil.find(item, InventoryUtil.PLACE_SCOPE);
        return r.found() ? r : null;
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!render.getValue()) return;

        long now = System.currentTimeMillis();
        double fadeMs = fadeTime.getValue() * 1000.0;

        for (Map.Entry<BlockPos, Long> entry : renderMap.entrySet()) {
            long age = now - entry.getValue();
            if (age > fadeMs) continue;

            double t = age / fadeMs;

            Color fc = fillColor.getValue();
            Color oc = outlineColor.getValue();

            RenderUtil.drawBoxFilled(event.getMatrix(), entry.getKey(),
                    withAlpha(fc, (int) (fc.getAlpha() * (1 - t))));
            RenderUtil.drawBox(event.getMatrix(), entry.getKey(),
                    withAlpha(oc, (int) (oc.getAlpha() * (1 - t))), 1.0f);
        }
    }

    private static Color withAlpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Mth.clamp(a, 0, 255));
    }
}
