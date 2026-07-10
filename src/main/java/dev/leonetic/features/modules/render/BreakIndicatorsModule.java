package dev.leonetic.features.modules.render;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.InteractionUtil;
import dev.leonetic.util.render.RenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.awt.Color;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public class BreakIndicatorsModule extends Module {

    private static final long TTL_MS = 2000L;

    private final Setting<Boolean> useDoubleminePrediction = bool("UseDoubleminePrediction", false).setPage("General");
    private final Setting<Float> rebreakCompletionAmount = num("RebreakCompletionAmount", 0.7f, 0.0f, 1.5f).setPage("General");
    private final Setting<Float> completionAmount = num("FullCompletionAmount", 1.0f, 0.0f, 1.5f).setPage("General");
    private final Setting<Float> removeCompletionAmount = num("ForceRemoveCompletionAmount", 1.3f, 0.0f, 1.5f).setPage("General");
    private final Setting<Boolean> ignoreFriends = bool("IgnoreFriends", false).setPage("General");

    private final Setting<Boolean> render = bool("DoRender", true).setPage("Render");
    private final Setting<Float> lineWidth = num("LineWidth", 1.5f, 0.5f, 5.0f).setPage("Render");
    private final Setting<Color> sideColor = color("SideColor", 255, 0, 80, 10).setPage("Render");
    private final Setting<Color> lineColor = color("LineColor", 255, 255, 255, 40).setPage("Render");

    private final Queue<BlockBreak> breakPackets = new ConcurrentLinkedQueue<>();
    private final Map<BlockPos, BlockBreak> breakStartTimes = new HashMap<>();

    public BreakIndicatorsModule() {
        super("BreakIndicators", "Renders the progress of a block being broken.", Category.RENDER);
    }

    @Override
    public void onEnable() {
        breakPackets.clear();
        breakStartTimes.clear();
    }

    @Override
    public void onDisable() {
        breakPackets.clear();
        breakStartTimes.clear();
    }

    @Subscribe
    private void onPacket(PacketEvent.Receive event) {
        if (nullCheck()) return;
        if (!(event.getPacket() instanceof ClientboundBlockDestructionPacket packet)) return;

        int progress = packet.getProgress();
        Entity entity = mc.level.getEntity(packet.getId());
        breakPackets.add(new BlockBreak(packet.getPos().immutable(), currentTick(0.0f), entity, progress));
    }

    public boolean isBlockBeingBroken(BlockPos blockPos) {
        return breakStartTimes.containsKey(blockPos);
    }

    @Subscribe
    private void onRender(Render3DEvent event) {
        if (nullCheck()) return;

        double currentTick = currentTick(event.getDelta());

        while (!breakPackets.isEmpty()) {
            BlockBreak breakEvent = breakPackets.remove();

            if (breakEvent.stage < 0 || breakEvent.stage > 9) {
                breakStartTimes.remove(breakEvent.blockPos);
                continue;
            }

            if (useDoubleminePrediction.getValue() && breakEvent.entity instanceof Player) {
                List<BlockBreak> playerBreakingBlocks = breakStartTimes.values().stream()
                        .filter(x -> x.entity == breakEvent.entity && !x.blockPos.equals(breakEvent.blockPos))
                        .sorted(Comparator.comparingDouble(x -> x.startTick))
                        .toList();

                if (playerBreakingBlocks.size() >= 2) {
                    breakStartTimes.remove(playerBreakingBlocks.getLast().blockPos);
                }
            }

            BlockBreak existing = breakStartTimes.get(breakEvent.blockPos);
            if (existing == null) {
                breakStartTimes.put(breakEvent.blockPos, breakEvent);
            } else {
                existing.update(breakEvent, currentTick);
            }
        }

        Iterator<Map.Entry<BlockPos, BlockBreak>> iterator = breakStartTimes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, BlockBreak> entry = iterator.next();
            BlockState state = mc.level.getBlockState(entry.getKey());

            if (System.currentTimeMillis() - entry.getValue().lastUpdate > TTL_MS
                    || state.isAir()
                    || entry.getValue().progress(currentTick) > removeCompletionAmount.getValue()
                    || !InteractionUtil.canBreak(entry.getKey(), state)) {
                iterator.remove();
            }
        }

        if (useDoubleminePrediction.getValue()) {
            Map<Player, List<BlockBreak>> playerBreakingBlocks = breakStartTimes.values().stream()
                    .sorted(Comparator.comparingDouble(blockBreak -> blockBreak.startTick))
                    .filter(blockBreak -> blockBreak.entity instanceof Player)
                    .collect(Collectors.groupingBy(blockBreak -> (Player) blockBreak.entity, Collectors.toList()));

            for (Map.Entry<Player, List<BlockBreak>> entry : playerBreakingBlocks.entrySet()) {
                entry.getValue().forEach(x -> x.isRebreak = false);

                if (entry.getValue().size() >= 2) {
                    entry.getValue().getLast().isRebreak = true;
                }
            }
        }

        if (!render.getValue()) return;

        for (Map.Entry<BlockPos, BlockBreak> entry : breakStartTimes.entrySet()) {
            if (ignoreFriends.getValue() && entry.getValue().entity instanceof Player player
                    && Homovore.friendManager.isFriend(player)) {
                continue;
            }

            entry.getValue().renderBlock(event, currentTick);
        }
    }

    private double currentTick(float partialTick) {
        return mc.level.getGameTime() + partialTick;
    }

    private class BlockBreak {
        private final BlockPos blockPos;
        private final double startTick;
        private Entity entity;
        private int stage;
        private long lastUpdate;
        private double lastUpdateTick;
        private double renderProgress;
        private double progressPerTick = 0.035;
        private boolean isRebreak;

        private BlockBreak(BlockPos blockPos, double startTick, Entity entity, int stage) {
            this.blockPos = blockPos;
            this.startTick = startTick;
            this.entity = entity;
            this.stage = stage;
            this.lastUpdate = System.currentTimeMillis();
            this.lastUpdateTick = startTick;
            this.renderProgress = stageProgress();
        }

        private void update(BlockBreak next, double currentTick) {
            double previousStageProgress = stageProgress();
            double nextStageProgress = next.stageProgress();
            double elapsed = Math.max(1.0, currentTick - lastUpdateTick);
            if (nextStageProgress > previousStageProgress) {
                progressPerTick = Math.clamp((nextStageProgress - previousStageProgress) / elapsed, 0.01, 0.12);
            }
            renderProgress = Math.max(progress(currentTick), nextStageProgress);
            entity = next.entity;
            stage = next.stage;
            lastUpdate = System.currentTimeMillis();
            lastUpdateTick = currentTick;
        }

        private void renderBlock(Render3DEvent event, double currentTick) {
            BlockState state = mc.level.getBlockState(blockPos);
            VoxelShape shape = state.getShape(mc.level, blockPos);
            AABB orig = shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds();

            double completion = isRebreak ? rebreakCompletionAmount.getValue() : completionAmount.getValue();
            double scale = completion <= 0.0 ? 1.0 : Math.clamp(progress(currentTick) / completion, 0.0, 1.0);

            double cx = (orig.minX + orig.maxX) * 0.5;
            double cy = (orig.minY + orig.maxY) * 0.5;
            double cz = (orig.minZ + orig.maxZ) * 0.5;
            double hx = (orig.maxX - orig.minX) * 0.5 * scale;
            double hy = (orig.maxY - orig.minY) * 0.5 * scale;
            double hz = (orig.maxZ - orig.minZ) * 0.5 * scale;

            AABB box = new AABB(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz)
                    .move(blockPos.getX(), blockPos.getY(), blockPos.getZ());

            RenderUtil.drawBoxFilled(event.getMatrix(), box, sideColor.getValue());
            RenderUtil.drawBox(event.getMatrix(), box, lineColor.getValue(), lineWidth.getValue());
        }

        private double progress(double currentTick) {
            double target = stageProgress();
            double animated = renderProgress + (currentTick - lastUpdateTick) * progressPerTick;
            return Math.max(target, animated);
        }

        private double stageProgress() {
            return (stage + 1) / 10.0;
        }
    }
}
