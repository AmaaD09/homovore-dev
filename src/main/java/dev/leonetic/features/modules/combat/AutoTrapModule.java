package dev.leonetic.features.modules.combat;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.TickEvent;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.modules.client.TargetsModule;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.manager.PlacementManager;
import dev.leonetic.util.PlaceUtil;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.ResultType;
import dev.leonetic.util.render.RenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AutoTrapModule extends Module {

    private static final int BOOST_DURATION_TICKS = 40;
    private static final double BOOST_DETECT_RANGE_SQ = 9.0;
    private static final long INTERCEPT_TIMEOUT_MS = 500L;

    public enum BlockType {
        OBSIDIAN(Items.OBSIDIAN),
        COBWEB(Items.COBWEB),
        HYBRID(null);

        final Item item;
        BlockType(Item item) { this.item = item; }
    }

    private final Setting<BlockType> block          = mode("Block", BlockType.OBSIDIAN).setPage("General");
    private final Setting<Double>  range            = num("TargetRange", 8.2, 1.0, 16.0).setPage("General");
    private final Setting<Boolean> pauseEat         = bool("PauseEat", true).setPage("General");
    private final Setting<Boolean> selfToggle       = bool("SelfToggle", true).setPage("General");
    private final Setting<Boolean> skipCrystalSpots = bool("SkipCrystalSpots", false).setPage("General");

    private final Setting<Boolean> prediction       = bool("Prediction", true).setPage("Prediction");
    private final Setting<Integer> predictionTicks  = num("PredictionTicks", 1, 0, 20).setPage("Prediction");
    private final Setting<Integer> elytraTicks      = num("ElytraPredictionTicks", 3, 0, 20).setPage("Prediction");
    private final Setting<Double>  lookBlend        = num("LookBlend", 0.6, 0.0, 1.0).setPage("Prediction");
    private final Setting<Boolean> faceFirst        = bool("FaceFirst", true).setPage("Prediction");
    private final Setting<Integer> faceLeadBlocks   = num("FaceLeadBlocks", 2, 0, 6).setPage("Prediction");

    private final Setting<Boolean> render           = bool("Render", true).setPage("Render");
    private final Setting<Float>   fadeTime         = num("FadeTime", 1.0f, 0.05f, 2.0f).setPage("Render");
    private final Setting<Color>   fillColor        = color("FillColor", 255, 50, 50, 40).setPage("Render");
    private final Setting<Color>   outlineColor     = color("OutlineColor", 255, 50, 50, 40).setPage("Render");

    private final Set<BlockPos> wantedPoses = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos> ownedQueued = new HashSet<>();
    private final Map<BlockPos, Long> renderMap = new HashMap<>();

    private LivingEntity target;
    private int cachedSlot = -1;

    private boolean buildInterceptNext = true;
    private long lastPlaceTime = 0L;
    private BlockPos lockedCenter;
    private Direction.Axis lockedPrimary, lockedSecondary, lockedTertiary;
    private int lockedPrimaryDir, lockedSecondaryDir, lockedTertiaryDir;
    private UUID lockedTargetUUID;

    private final Map<UUID, Integer> boostingTicks = new HashMap<>();

    private final PlacementManager.PlacementListener airRefillListener = (pos, nowAir) -> {
        ownedQueued.remove(pos);
        if (!nowAir || !wantedPoses.contains(pos)) return;
        int slot = cachedSlot;
        if (slot < 0 || !PlaceUtil.canPlace(pos)) return;
        if (Homovore.placementManager.enqueue(pos, slot)) {
            ownedQueued.add(pos);
        }
    };

    public AutoTrapModule() {
        super("AutoTrap", "Traps the closest target in obsidian, cobweb, or both to lock down their movement.", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        Homovore.placementManager.addListener(airRefillListener);
        target = null;
        resetInterceptLock();
        boostingTicks.clear();
        lastPlaceTime = System.currentTimeMillis();
    }

    @Override
    public void onDisable() {
        Homovore.placementManager.removeListener(airRefillListener);
        Homovore.placementManager.removeQueuedFor(ownedQueued::contains);
        ownedQueued.clear();
        wantedPoses.clear();
        renderMap.clear();
        boostingTicks.clear();
        resetInterceptLock();
        target = null;
        cachedSlot = -1;
    }

    private void resetInterceptLock() {
        buildInterceptNext = true;
        lockedCenter = null;
        lockedPrimary = lockedSecondary = lockedTertiary = null;
        lockedPrimaryDir = lockedSecondaryDir = lockedTertiaryDir = 0;
        lockedTargetUUID = null;
    }

    @Subscribe(priority = 1)
    private void onPacket(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof ClientboundSoundPacket pkt)) return;
        if (pkt.getSound().value() != SoundEvents.FIREWORK_ROCKET_LAUNCH) return;
        double sx = pkt.getX(), sy = pkt.getY(), sz = pkt.getZ();
        mc.execute(() -> {
            if (nullCheck()) return;
            for (Player p : mc.level.players()) {
                double dx = p.getX() - sx, dy = p.getY() - sy, dz = p.getZ() - sz;
                if (dx * dx + dy * dy + dz * dz < BOOST_DETECT_RANGE_SQ) {
                    boostingTicks.put(p.getUUID(), BOOST_DURATION_TICKS);
                    break;
                }
            }
        });
    }

    @Subscribe(priority = 1)
    private void onTick(TickEvent event) {
        if (nullCheck() || mc.screen != null) return;

        boostingTicks.entrySet().removeIf(e -> {
            int next = e.getValue() - 1;
            e.setValue(next);
            return next <= 0;
        });

        int slot = resolveSlot();
        if (slot < 0) {
            cachedSlot = -1;
            wantedPoses.clear();
            return;
        }
        cachedSlot = slot;

        LivingEntity sel = findTarget();
        if (target == null || !isValidStillTarget(target)) {
            target = sel;
            if (target == null) {
                pruneAll();
                resetInterceptLock();
                if (selfToggle.getValue()) disable();
                return;
            }
        }

        if (lockedTargetUUID != null && !lockedTargetUUID.equals(target.getUUID())) {
            resetInterceptLock();
        }
        if (lockedTargetUUID != null && !target.isFallFlying()) {
            resetInterceptLock();
        }

        if (pauseEat.getValue() && mc.player.isUsingItem()) return;

        OffhandModule offhand = Homovore.moduleManager.getModuleByClass(OffhandModule.class);
        if (offhand != null && offhand.shouldDeferForEat()) return;

        if (target.isFallFlying() && !buildInterceptNext
                && System.currentTimeMillis() - lastPlaceTime > INTERCEPT_TIMEOUT_MS) {
            resetInterceptLock();
        }

        Vec3 predicted = predictPosition(target);
        Set<BlockPos> placePoses;

        if (target.isFallFlying()) {
            placePoses = buildInterceptNext
                    ? elytraInterceptWedge(predicted, target)
                    : elytraCloseShell(target);
        } else if (isCubeHitbox(target)) {
            placePoses = cubeTrap(predicted, target);
        } else {
            placePoses = standingTrap(predicted, target);
        }

        wantedPoses.clear();
        wantedPoses.addAll(placePoses);

        List<BlockPos> reachable = new ArrayList<>();
        Vec3 eye = mc.player.getEyePosition();
        for (BlockPos pos : placePoses) {
            if (eye.distanceTo(Vec3.atCenterOf(pos)) <= 5.1) {
                reachable.add(pos.immutable());
            }
        }
        if (reachable.isEmpty()) {
            pruneOwned();
            return;
        }

        Vec3 sortPoint = new Vec3(predicted.x, target.getY(), predicted.z);
        reachable.sort(Comparator.comparingDouble(p -> Vec3.atCenterOf(p).distanceToSqr(sortPoint)));

        if (faceFirst.getValue()) {
            Direction lookDir = target.getDirection();
            BlockPos anchor = target.isFallFlying()
                    ? BlockPos.containing(predicted)
                    : target.blockPosition();

            BlockPos leadBase = isCubeHitbox(target) ? anchor : anchor.above();
            BlockPos prio = leadBase.relative(lookDir, faceLeadBlocks.getValue());
            if (reachable.remove(prio)) {
                reachable.add(0, prio);
            } else if (wantedPoses.contains(prio) && eye.distanceTo(Vec3.atCenterOf(prio)) <= 5.1) {
                reachable.add(0, prio);
            }
        }

        if (target.isFallFlying() && eye.distanceTo(predicted) > 3.5) {
            if (buildInterceptNext) resetInterceptLock();
            return;
        }

        pruneOwned();

        long now = System.currentTimeMillis();
        boolean placedAny = false;
        for (BlockPos pos : reachable) {
            BlockState state = mc.level.getBlockState(pos);
            if (!state.canBeReplaced()) continue;
            if (!PlaceUtil.canPlace(pos)) continue;
            if (Homovore.placementManager.enqueue(pos, cachedSlot)) {
                ownedQueued.add(pos);
                renderMap.put(pos, now);
                placedAny = true;
            }
        }
        if (placedAny) lastPlaceTime = now;

        if (target.isFallFlying() && placedAny && buildInterceptNext) {
            buildInterceptNext = false;
        }

        long fadeMs = (long) (fadeTime.getValue() * 1000);
        renderMap.entrySet().removeIf(e -> now - e.getValue() > fadeMs);
    }

    private static boolean isCubeHitbox(LivingEntity e) {
        return e.isFallFlying() || e.isVisuallyCrawling() || e.isSwimming();
    }

    private Set<BlockPos> standingTrap(Vec3 predicted, LivingEntity entity) {
        LinkedHashSet<BlockPos> poses = new LinkedHashSet<>();
        double half = entity.getBbWidth() * 0.5;

        int x0 = PlaceUtil.minCell(predicted.x - half);
        int x1 = PlaceUtil.maxCell(predicted.x + half);
        int z0 = PlaceUtil.minCell(predicted.z - half);
        int z1 = PlaceUtil.maxCell(predicted.z + half);
        int feetY = entity.blockPosition().getY();
        int headY = feetY + 1;
        int capY  = feetY + 2;

        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                poses.add(new BlockPos(x, capY, z));
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    int nx = x + d.getStepX();
                    int nz = z + d.getStepZ();
                    if (nx >= x0 && nx <= x1 && nz >= z0 && nz <= z1) continue;
                    poses.add(new BlockPos(nx, headY, nz));
                }
            }
        }

        if (!entity.onGround()) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    poses.add(new BlockPos(x, feetY - 1, z));
                }
            }
        }
        return poses;
    }

    private Set<BlockPos> cubeTrap(Vec3 predicted, LivingEntity entity) {
        LinkedHashSet<BlockPos> poses = new LinkedHashSet<>();
        double half = entity.getBbWidth() * 0.5;
        double height = entity.getBbHeight();

        int x0 = PlaceUtil.minCell(predicted.x - half);
        int x1 = PlaceUtil.maxCell(predicted.x + half);
        int z0 = PlaceUtil.minCell(predicted.z - half);
        int z1 = PlaceUtil.maxCell(predicted.z + half);
        int y0 = PlaceUtil.minCell(entity.getY());
        int y1 = PlaceUtil.maxCell(entity.getY() + height);

        BlockPos feetBlock = entity.blockPosition();
        Set<BlockPos> crystalSpots = null;
        if (skipCrystalSpots.getValue()) {
            crystalSpots = new HashSet<>(4);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                crystalSpots.add(feetBlock.relative(d));
            }
        }

        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                for (int y = y0 - 1; y <= y1 + 1; y++) {
                    int outsideX = (x < x0 || x > x1) ? 1 : 0;
                    int outsideZ = (z < z0 || z > z1) ? 1 : 0;
                    int outsideY = (y < y0 || y > y1) ? 1 : 0;
                    if (outsideX + outsideZ + outsideY != 1) continue;
                    BlockPos pos = new BlockPos(x, y, z);
                    if (crystalSpots != null && crystalSpots.contains(pos)) continue;
                    poses.add(pos);
                }
            }
        }
        return poses;
    }

    private Set<BlockPos> elytraInterceptWedge(Vec3 predicted, LivingEntity entity) {
        LinkedHashSet<BlockPos> poses = new LinkedHashSet<>();
        lockedCenter = BlockPos.containing(predicted);
        lockedTargetUUID = entity.getUUID();
        Vec3 vel = entity.getDeltaMovement();
        if (vel.lengthSqr() < 1e-6) {

            Vec3 look = entity.getLookAngle();
            vel = look.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : look;
        }
        Direction.Axis[] axes = Direction.Axis.values();

        double[] mag = new double[]{
                Math.abs(vel.x), Math.abs(vel.y), Math.abs(vel.z)
        };

        int[] order = {0, 1, 2};
        for (int i = 0; i < 2; i++) {
            int max = i;
            for (int j = i + 1; j < 3; j++) if (mag[order[j]] > mag[order[max]]) max = j;
            if (max != i) { int t = order[i]; order[i] = order[max]; order[max] = t; }
        }
        lockedPrimary   = axes[order[0]];
        lockedSecondary = axes[order[1]];
        lockedTertiary  = axes[order[2]];
        lockedPrimaryDir   = signOr1(componentAlong(vel, lockedPrimary));
        lockedSecondaryDir = signOr1(componentAlong(vel, lockedSecondary));
        lockedTertiaryDir  = signOr1(componentAlong(vel, lockedTertiary));

        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                poses.add(offset(offset(offset(lockedCenter,
                        lockedPrimary,   lockedPrimaryDir),
                        lockedSecondary, i * lockedSecondaryDir),
                        lockedTertiary,  j * lockedTertiaryDir));
            }
        }
        for (int i = 0; i < 2; i++) {
            poses.add(offset(offset(lockedCenter,
                    lockedSecondary, 2 * lockedSecondaryDir),
                    lockedTertiary,  i * lockedTertiaryDir));
            poses.add(offset(offset(lockedCenter,
                    lockedTertiary,  2 * lockedTertiaryDir),
                    lockedSecondary, i * lockedSecondaryDir));
        }
        return poses;
    }

    private Set<BlockPos> elytraCloseShell(LivingEntity entity) {
        LinkedHashSet<BlockPos> poses = new LinkedHashSet<>();

        var bb = entity.getBoundingBox();
        int feetY = entity.blockPosition().getY();
        int minX = PlaceUtil.minCell(bb.minX);
        int maxX = PlaceUtil.maxCell(bb.maxX);
        int minZ = PlaceUtil.minCell(bb.minZ);
        int maxZ = PlaceUtil.maxCell(bb.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos feetPos = new BlockPos(x, feetY, z);
                for (int ox = -1; ox <= 1; ox++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        if (Math.abs(ox) + Math.abs(oz) != 1) continue;
                        BlockPos adj = feetPos.offset(ox, 0, oz);
                        if (canFill(adj)) poses.add(adj);
                        BlockPos head = adj.above();
                        if (canFill(head)) poses.add(head);
                    }
                }
                BlockPos below = feetPos.below();
                if (canFill(below)) poses.add(below);
                BlockPos top = feetPos.above(2);
                if (canFill(top)) poses.add(top);
            }
        }
        return poses;
    }

    private boolean canFill(BlockPos pos) {
        BlockState s = mc.level.getBlockState(pos);
        return s.isAir() || s.canBeReplaced();
    }

    private static double componentAlong(Vec3 v, Direction.Axis axis) {
        return switch (axis) {
            case X -> v.x;
            case Y -> v.y;
            case Z -> v.z;
        };
    }

    private static BlockPos offset(BlockPos pos, Direction.Axis axis, int n) {
        return switch (axis) {
            case X -> pos.offset(n, 0, 0);
            case Y -> pos.offset(0, n, 0);
            case Z -> pos.offset(0, 0, n);
        };
    }

    private static int signOr1(double v) {
        int s = (int) Math.signum(v);
        return s == 0 ? 1 : s;
    }

    private void pruneOwned() {
        if (ownedQueued.isEmpty()) return;
        Homovore.placementManager.removeQueuedFor(p -> ownedQueued.contains(p) && !wantedPoses.contains(p));
        ownedQueued.removeIf(p -> !wantedPoses.contains(p));
    }

    private void pruneAll() {
        if (!ownedQueued.isEmpty()) {
            Homovore.placementManager.removeQueuedFor(ownedQueued::contains);
            ownedQueued.clear();
        }
        wantedPoses.clear();
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

    @Override
    public String getDisplayInfo() {
        return wantedPoses.isEmpty() ? null : String.valueOf(wantedPoses.size());
    }

    private int resolveSlot() {
        if (block.getValue() == BlockType.HYBRID) {
            int web = hotbarSlotOf(Items.COBWEB);
            return web >= 0 ? web : hotbarSlotOf(Items.OBSIDIAN);
        }
        return hotbarSlotOf(block.getValue().item);
    }

    private int hotbarSlotOf(Item item) {
        var r = InventoryUtil.find(item, InventoryUtil.PLACE_SCOPE);
        return (r.found() && r.type() != ResultType.OFFHAND) ? r.slot() : -1;
    }

    private LivingEntity findTarget() {
        TargetsModule targets = Homovore.moduleManager.getModuleByClass(TargetsModule.class);
        double maxSq = range.getValue() * range.getValue();
        LivingEntity best = null;
        double bestSq = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity living)) continue;

            if (targets != null && !targets.isValidPlayerTarget(e)) continue;
            double dSq = mc.player.distanceToSqr(e);
            if (dSq > maxSq || dSq >= bestSq) continue;
            bestSq = dSq;
            best = living;
        }
        return best;
    }

    private boolean isValidStillTarget(LivingEntity e) {
        if (e == null || !e.isAlive()) return false;
        if (mc.player.distanceToSqr(e) > range.getValue() * range.getValue()) return false;
        TargetsModule targets = Homovore.moduleManager.getModuleByClass(TargetsModule.class);
        return targets == null || targets.isValidPlayerTarget(e);
    }

    private Vec3 predictPosition(LivingEntity entity) {
        if (!prediction.getValue()) return entity.position();

        if (isAlreadyTrapped(entity)) return entity.position();
        int ticks = entity.isFallFlying() ? elytraTicks.getValue() : predictionTicks.getValue();
        if (ticks <= 0) return entity.position();
        if (entity.isFallFlying()) {
            return boostingTicks.containsKey(entity.getUUID())
                    ? simulateBoostedElytra(entity, ticks)
                    : simulateElytra(entity, ticks);
        }
        return simulateGround(entity, ticks);
    }

    private boolean isAlreadyTrapped(LivingEntity entity) {
        if (entity.isSwimming()) return false;
        BlockPos pos = entity.blockPosition();
        if (mc.level.getBlockState(pos.below()).canBeReplaced()) return false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (mc.level.getBlockState(pos.above().relative(d)).canBeReplaced()) return false;
        }
        return !mc.level.getBlockState(pos.above(2)).canBeReplaced();
    }

    private Vec3 hybridHorizVel(LivingEntity entity) {
        double dx = entity.getX() - entity.xo;
        double dz = entity.getZ() - entity.zo;
        double speed = Math.hypot(dx, dz);
        if (speed < 1e-4) return Vec3.ZERO;
        float yawRad = (float) Math.toRadians(entity.getYRot());
        double lookX = -Math.sin(yawRad);
        double lookZ =  Math.cos(yawRad);
        double bias = lookBlend.getValue() * Math.min(1.0, speed / 0.21);
        return new Vec3(
                dx * (1 - bias) + lookX * speed * bias,
                0,
                dz * (1 - bias) + lookZ * speed * bias);
    }

    private Vec3 simulateGround(LivingEntity entity, int ticks) {
        Vec3 pos = entity.position();
        Vec3 horiz = hybridHorizVel(entity);
        double vy = entity.getY() - entity.yo;
        boolean onGround = entity.onGround();
        if (onGround && vy <= 0.05) vy = 0.0;
        Vec3 vel = new Vec3(horiz.x, vy, horiz.z);
        for (int i = 0; i < ticks; i++) {
            Vec3 nextPos = pos.add(vel);
            if (vel.y != 0.0) {
                Vec3 rayStart = pos.add(0, 0.5, 0);
                Vec3 rayEnd = nextPos.add(0, 0.5, 0);
                BlockHitResult hit = mc.level.clip(new ClipContext(rayStart, rayEnd,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
                if (hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP) {
                    return new Vec3(nextPos.x, hit.getBlockPos().getY() + 1.0, nextPos.z);
                }
            }
            pos = nextPos;
            if (!onGround) {
                vel = new Vec3(vel.x * 0.98, (vel.y - 0.08) * 0.98, vel.z * 0.98);
            }
            if (vel.lengthSqr() < 1e-4) break;
        }
        return pos;
    }

    private Vec3 simulateElytra(LivingEntity entity, int ticks) {
        Vec3 pos = entity.position();
        Vec3 tickVel = new Vec3(
                entity.getX() - entity.xo,
                entity.getY() - entity.yo,
                entity.getZ() - entity.zo);
        Vec3 look = entity.getLookAngle();
        double speed = tickVel.length();
        double bias = lookBlend.getValue();
        Vec3 vel = new Vec3(
                tickVel.x * (1 - bias) + look.x * speed * bias,
                tickVel.y * (1 - bias) + look.y * speed * bias,
                tickVel.z * (1 - bias) + look.z * speed * bias);
        float pitchRad = (float) Math.toRadians(entity.getXRot());
        double cos = Math.cos(pitchRad);
        for (int i = 0; i < ticks; i++) {
            pos = pos.add(vel);
            double horizSpeed = Math.hypot(vel.x, vel.z);
            double len = vel.length();
            double liftFactor = cos * cos * Math.min(1.0, len / 0.4);
            double vy = vel.y + (-0.04 + liftFactor * 0.06);
            if (vel.y < 0.0 && horizSpeed > 0.0) vy += -0.1 * vel.y * liftFactor;
            Vec3 vAfterLift = new Vec3(vel.x, vy, vel.z);
            Vec3 align = new Vec3(
                    look.x * 0.1 + (look.x * 1.5 - vAfterLift.x) * 0.01,
                    look.y * 0.1 + (look.y * 1.5 - vAfterLift.y) * 0.01,
                    look.z * 0.1 + (look.z * 1.5 - vAfterLift.z) * 0.01);
            Vec3 vAligned = vAfterLift.add(align);
            vel = new Vec3(vAligned.x * 0.99, vAligned.y * 0.98, vAligned.z * 0.99);
        }
        return pos;
    }

    private Vec3 simulateBoostedElytra(LivingEntity entity, int ticks) {
        Vec3 pos = entity.position();
        Vec3 vel = new Vec3(
                entity.getX() - entity.xo,
                entity.getY() - entity.yo,
                entity.getZ() - entity.zo);
        for (int i = 0; i < ticks; i++) {
            pos = pos.add(vel);
            double vy = Math.max(vel.y, -3.92);
            vel = new Vec3(vel.x * 0.991, vy, vel.z * 0.991);
        }
        return pos;
    }

    private static Color withAlpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Mth.clamp(a, 0, 255));
    }
}
