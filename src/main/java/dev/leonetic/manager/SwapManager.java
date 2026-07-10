package dev.leonetic.manager;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.TickEvent;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.Feature;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.ResultType;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SwapManager extends Feature {
    private SwapHandle active;

    private SwapHandle suspended;

    private int serverSlot = -1;

    private int homeSlot = -1;

    private boolean wasUsing = false;
    private Item useItem = null;
    private InteractionHand useHand = null;
    private int useTotalTicks = 0;
    private int lastRemainingTicks = 0;

    private int useElapsedTicks = 0;
    private int useResetCount = 0;

    private int stalledTicks = 0;
    private boolean frozenLogged = false;
    private boolean overdurationLogged = false;

    private static final int STALL_WARN_TICKS = 4;
    private static final int OVER_DURATION_SLACK = 2;

    public void init() {
        EVENT_BUS.register(this);
    }

    @Subscribe
    private void onPacketSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundSetCarriedItemPacket pkt) {
            if (pkt.getSlot() != serverSlot) {

                String during = wasUsing
                        ? " DURING-USE(" + name(useItem) + ", " + lastRemainingTicks + "t left) by=" + sender()
                        : "";
                log("carried slot " + serverSlot + " -> " + pkt.getSlot()
                        + " (activeSwap=" + holderStr() + ")" + during);
            }
            serverSlot = pkt.getSlot();
        } else if (wasUsing
                && event.getPacket() instanceof ServerboundPlayerActionPacket pa
                && pa.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) {

            log("offhand-swap action DURING-USE(" + name(useItem) + ", " + lastRemainingTicks
                    + "t left) by=" + sender());
        } else if (wasUsing
                && event.getPacket() instanceof ServerboundContainerClickPacket cc) {

            log("container-click DURING-USE(" + name(useItem) + ", " + lastRemainingTicks
                    + "t left) container=" + cc.containerId() + " slot=" + cc.slotNum()
                    + " button=" + cc.buttonNum()
                    + " (activeSwap=" + holderStr() + ") by=" + sender());
        }
    }

    @Subscribe
    private void onTick(TickEvent event) {
        if (mc.player == null) { wasUsing = false; homeSlot = -1; return; }

        if (active == null && homeSlot != -1) {
            if (InventoryUtil.selected() != homeSlot) {
                mc.player.getInventory().setSelectedSlot(homeSlot);
                mc.gameMode.ensureHasSentCarriedItem();
                log("restore home -> " + homeSlot);
            }
            homeSlot = -1;
        }

        boolean using = mc.player.isUsingItem();
        if (using && !wasUsing) {
            ItemStack stack = mc.player.getUseItem();
            useItem = stack.getItem();
            useHand = mc.player.getUsedItemHand();
            useTotalTicks = stack.getItem().getUseDuration(stack, mc.player);
            lastRemainingTicks = mc.player.getUseItemRemainingTicks();
            useElapsedTicks = 0;
            useResetCount = 0;
            stalledTicks = 0;
            frozenLogged = false;
            overdurationLogged = false;
            log("use START " + name(useItem) + " hand=" + useHand
                    + " duration=" + useTotalTicks + "t (activeSwap=" + holderStr() + ")");
        } else if (using) {
            useElapsedTicks++;

            Item now = mc.player.getUseItem().getItem();
            if (now != useItem) {
                log("use ITEM-CHANGED mid-use " + name(useItem) + " -> " + name(now)
                        + " (activeSwap=" + holderStr() + ")");
                useItem = now;
            }

            int remaining = mc.player.getUseItemRemainingTicks();
            if (remaining > lastRemainingTicks + 1) {
                useResetCount++;
                log("use RESET " + name(useItem) + " countdown " + lastRemainingTicks + " -> " + remaining
                        + "t (restart #" + useResetCount + ", elapsed=" + useElapsedTicks
                        + "t, activeSwap=" + holderStr() + ")");
                stalledTicks = 0;
            } else if (remaining == lastRemainingTicks) {

                stalledTicks++;
                if (stalledTicks >= STALL_WARN_TICKS && !frozenLogged) {
                    frozenLogged = true;
                    log("use STALL " + name(useItem) + " countdown frozen at " + remaining
                            + "t for " + stalledTicks + "t (elapsed=" + useElapsedTicks
                            + "t, resets=" + useResetCount + ", activeSwap=" + holderStr()
                            + ") — likely GAP-FAIL");
                }
            } else {
                stalledTicks = 0;
            }

            if (!overdurationLogged && useTotalTicks > 0
                    && useElapsedTicks > useTotalTicks + OVER_DURATION_SLACK) {
                overdurationLogged = true;
                log("use OVER-DURATION " + name(useItem) + " still using after " + useElapsedTicks
                        + "t (expected " + useTotalTicks + "t, remaining=" + remaining
                        + ", resets=" + useResetCount + ", activeSwap=" + holderStr()
                        + ") — likely GAP-FAIL");
            }

            lastRemainingTicks = remaining;
        } else if (wasUsing) {
            if (lastRemainingTicks > 2) {
                log("use STOP " + name(useItem) + " INTERRUPTED after " + useElapsedTicks + "t ("
                        + lastRemainingTicks + "t left of " + useTotalTicks + ", resets=" + useResetCount
                        + ", stalls=" + stalledTicks + ") — likely GAP-FAIL");
            } else {
                log("use STOP " + name(useItem) + " completed in " + useElapsedTicks + "t (duration="
                        + useTotalTicks + "t, resets=" + useResetCount + ")");
            }
            useItem = null;
            useHand = null;
        }
        wasUsing = using;
    }

    private String holderStr() {
        return active == null ? "none" : active.id + "/" + active.priority;
    }

    private static String sender() {
        for (StackTraceElement f : Thread.currentThread().getStackTrace()) {
            String cls = f.getClassName();
            if (!cls.startsWith("dev.leonetic.")) continue;
            if (cls.endsWith("SwapManager") || cls.contains(".inventory.")
                    || cls.contains(".event.")) continue;
            int dot = cls.lastIndexOf('.');
            return cls.substring(dot + 1) + "." + f.getMethodName() + ":" + f.getLineNumber();
        }
        return "?";
    }

    private static String name(Item item) {
        if (item == null) return "null";
        if (item == Items.TOTEM_OF_UNDYING) return "TOTEM";
        if (item == Items.ENCHANTED_GOLDEN_APPLE) return "GAPPLE";
        if (item == Items.GOLDEN_APPLE) return "APPLE";
        return item.toString();
    }

    private static void log(String msg) {
        Homovore.LOGGER.info("[Swap] {}", msg);
    }

    public int serverSlot() {
        if (mc.player == null) return serverSlot;
        return serverSlot == -1 ? InventoryUtil.selected() : serverSlot;
    }

    public boolean submit(SwapRequest req) {
        if (!req.target.found()) return false;
        if (req.target.holding()) {
            req.action.accept(req.target);
            return true;
        }
        boolean silent = req.silent || req.target.type() == ResultType.INVENTORY;
        SwapHandle h = acquire(req.id, req.priority, false, silent);
        if (h == null) return false;
        int last = h.originalSlot;
        try {
            boolean swapped = silent ? InventoryUtil.swapSilent(req.target) : InventoryUtil.swap(req.target);
            if (!swapped) return false;
            try {
                req.action.accept(req.target);
            } finally {
                if (silent) InventoryUtil.swapBackSilent(req.target);
                else InventoryUtil.swapBack(req.target, last);
            }
            return true;
        } finally {
            if (active == h) resumeOrClear();
            h.released = true;
        }
    }

    private boolean altSwap(SwapRequest req) {
        int held = serverSlot();
        if (!InventoryUtil.altSwapInto(req.target, held)) return false;
        try {
            req.action.accept(req.target);
        } finally {
            InventoryUtil.altSwapInto(req.target, held);
        }
        return true;
    }

    // True when a non-lease swap actively owns the hotbar right now. Leases
    // (e.g. Offhand's gapple hold) are borrowable and handled by acquire.
    public boolean isHotbarBusy() {
        return active != null && !active.released && !active.lease;
    }

    private boolean blockedByActiveHold(String id, int priority) {
        return active != null && !active.released && !active.lease
                && !active.id.equals(id) && active.priority >= priority;
    }

    public SwapHandle acquire(String id, int priority) {
        return acquire(id, priority, false, false);
    }

    public SwapHandle acquireLease(String id, int priority) {
        return acquire(id, priority, true, false);
    }

    private SwapHandle acquire(String id, int priority, boolean lease, boolean altSwap) {
        if (nullCheck()) return null;
        if (altSwap) {
            if (active != null)
                log("acquire " + id + "/" + priority + " alt-borrows " + active.id + "/" + active.priority);
            return new SwapHandle(id, priority, InventoryUtil.selected());
        }
        if (active != null) {
            if (active.priority >= priority) {
                log("acquire DENIED " + id + "/" + priority + " — held by " + active.id + "/" + active.priority);
                return null;
            } else if (active.lease) {
                suspended = active;
                log("acquire " + id + "/" + priority + " borrows lease " + active.id + "/" + active.priority);
            } else {
                log("acquire " + id + "/" + priority + " preempts " + active.id + "/" + active.priority);
                active.released = true;
                if (active.onPreempt != null) active.onPreempt.run();
            }
        } else {
            log("acquire " + id + "/" + priority);
        }
        if (homeSlot == -1) homeSlot = InventoryUtil.selected();
        SwapHandle h = new SwapHandle(id, priority, InventoryUtil.selected());
        h.lease = lease;
        active = h;
        return h;
    }

    public void release(SwapHandle h) {
        if (h == null || h.released) return;
        h.released = true;
        if (suspended == h) suspended = null;
        if (active != h) return;
        log("release " + h.id + "/" + h.priority);
        resumeOrClear();
    }

    private void resumeOrClear() {
        if (suspended != null && !suspended.released) {
            active = suspended;
            suspended = null;
            log("resume " + active.id + "/" + active.priority);
        } else {
            suspended = null;
            active = null;
        }
    }

    public boolean holdsActive(SwapHandle h) {
        return h != null && active == h && !h.released;
    }

    // True while the handle is still ours: active, or merely suspended by a
    // higher-priority borrow that will resume it. Unlike holdsActive, this stays
    // true across a borrow, so a lease holder can keep its reservation instead of
    // discarding it (and then being denied re-acquisition by the active swap).
    public boolean holds(SwapHandle h) {
        return h != null && !h.released && (active == h || suspended == h);
    }

    public boolean isBlocked(int priority) {
        return active != null && active.priority >= priority;
    }

    public boolean isBlockedByOther(String id, int priority) {
        return active != null && !active.id.equals(id) && active.priority >= priority;
    }

    public static final class SwapHandle {
        public final String id;
        public final int priority;
        public final int originalSlot;
        boolean released;
        boolean lease;
        Runnable onPreempt;

        SwapHandle(String id, int priority, int originalSlot) {
            this.id = id;
            this.priority = priority;
            this.originalSlot = originalSlot;
        }

        public boolean isReleased() { return released; }

        public void onPreempt(Runnable r) { this.onPreempt = r; }
    }
}
