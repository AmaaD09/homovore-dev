package dev.leonetic.features.modules.client;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.network.DisconnectEvent;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;

import java.util.ArrayDeque;
import java.util.Deque;

public class TestLimitModule extends Module {

    private static final long MANUAL_TOKEN_EXPIRY_MS = 1000L;

    public final Setting<Integer> limit = num("Limit", 79, 1, 500);
    public final Setting<Integer> timing = num("Timing", 4200, 100, 20000);

    private final Deque<Long> timestamps = new ArrayDeque<>();
    private final Deque<Long> pendingTokens = new ArrayDeque<>();

    public TestLimitModule() {
        super("TestLimit", "Throttles ClickSlot packets to stay under the server's inventory rate limit", Category.CLIENT);
    }

    public static TestLimitModule get() {
        if (Homovore.moduleManager == null) return null;
        return Homovore.moduleManager.getModuleByClass(TestLimitModule.class);
    }

    @Override
    public void onEnable() {
        clear();
    }

    @Override
    public void onDisable() {
        clear();
    }

    @Subscribe
    private void onDisconnect(DisconnectEvent event) {
        clear();
    }

    @Subscribe
    private void onPacketSend(PacketEvent.Send event) {
        if (event.isCancelled()) return;
        if (!(event.getPacket() instanceof ServerboundContainerClickPacket)) return;

        long now = System.currentTimeMillis();
        synchronized (timestamps) {
            if (consumeToken(now)) return;

            prune(now);
            if (timestamps.size() >= limit.getValue()) {
                event.cancel();
                return;
            }
            timestamps.addLast(now);
        }
    }

    public boolean tryAcquire() {
        long now = System.currentTimeMillis();
        synchronized (timestamps) {
            prune(now);
            if (timestamps.size() >= limit.getValue()) return false;
            timestamps.addLast(now);
            pendingTokens.addLast(now);
            return true;
        }
    }

    public boolean isLimited() {
        return willBeLimited(0);
    }

    public boolean willBeLimited(int additional) {
        synchronized (timestamps) {
            prune(System.currentTimeMillis());
            return timestamps.size() + additional >= limit.getValue();
        }
    }

    public int count() {
        synchronized (timestamps) {
            prune(System.currentTimeMillis());
            return timestamps.size();
        }
    }

    public int getLimit() {
        return limit.getValue();
    }

    public long timeUntilResetMs() {
        long now = System.currentTimeMillis();
        synchronized (timestamps) {
            prune(now);
            Long first = timestamps.peekFirst();
            if (first == null) return 0L;
            return Math.max(0L, timing.getValue() - (now - first));
        }
    }

    @Override
    public String getDisplayInfo() {
        return count() + "/" + getLimit();
    }

    private void prune(long now) {
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= timing.getValue()) {
            timestamps.pollFirst();
        }
    }

    private boolean consumeToken(long now) {
        while (!pendingTokens.isEmpty() && now - pendingTokens.peekFirst() >= MANUAL_TOKEN_EXPIRY_MS) {
            pendingTokens.pollFirst();
        }
        if (pendingTokens.isEmpty()) return false;
        pendingTokens.pollFirst();
        return true;
    }

    private void clear() {
        synchronized (timestamps) {
            timestamps.clear();
            pendingTokens.clear();
        }
    }
}
