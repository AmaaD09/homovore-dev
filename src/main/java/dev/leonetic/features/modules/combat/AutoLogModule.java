package dev.leonetic.features.modules.combat;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.TickEvent;
import dev.leonetic.event.impl.input.KeyInputEvent;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Bind;
import dev.leonetic.features.settings.Setting;
import io.netty.channel.Channel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AutoLogModule extends Module {

    private final Setting<Boolean> logOnHealth = bool("LogOnHealth", true);
    private final Setting<Integer> healthThreshold = num("HealthThreshold", 6, 1, 36);
    private final Setting<Boolean> logOnTotems = bool("LogOnTotems", true);
    private final Setting<Integer> totemThreshold = num("TotemThreshold", 1, 0, 64);
    private final Setting<Bind> disconnectBind = key("DisconnectBind", Bind.none());

    private int cachedTotems = 0;
    private volatile boolean awaitingRechamber = false;
    private List<String> nearbyPlayerNames = new ArrayList<>();

    public AutoLogModule() {
        super("AutoLog", "Automatically disconnects based on health or totem count.", Category.COMBAT);
        healthThreshold.setVisibility(v -> logOnHealth.getValue());
        totemThreshold.setVisibility(v -> logOnTotems.getValue());
    }

    @Override
    public void onEnable() {
        awaitingRechamber = false;
        if (!nullCheck()) updateTotemCount();
    }

    @Override
    public void onDisable() {
        awaitingRechamber = false;
    }

    @Subscribe
    private void onTick(TickEvent event) {
        if (nullCheck()) return;
        if (mc.screen != null) return;
        if (mc.player.isDeadOrDying()) { disable(); return; }
        updateTotemCount();
        nearbyPlayerNames = mc.level.players().stream()
                .filter(p -> p != mc.player)
                .map(p -> p.getGameProfile().name())
                .toList();
        if (!awaitingRechamber && isSurvival()) checkThresholds();
    }

    @Subscribe
    private void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck() || !isSurvival()) return;
        if (event.getPacket() instanceof ClientboundEntityEventPacket pkt) {
            if (pkt.getEventId() == 35 && pkt.getEntity(mc.level) == mc.player) {
                cachedTotems--;
                if (!logOnTotems.getValue() || cachedTotems > totemThreshold.getValue()) return;

                if (awaitingRechamber) {
                    awaitingRechamber = false;
                    mc.execute(this::disconnect);
                    return;
                }

                boolean pollOffhand = totemThreshold.getValue() > 1 && isOffhandModuleEnabled();
                Channel channel = pollOffhand ? getChannel() : null;
                if (channel == null) {
                    disconnect();
                    return;
                }
                awaitingRechamber = true;
                pollForRechamber(channel, System.currentTimeMillis() + 500);
            }
        } else if (event.getPacket() instanceof ClientboundSetHealthPacket pkt) {
            if (pkt.getHealth() <= 0) { disable(); return; }
            if (logOnHealth.getValue() && pkt.getHealth() <= healthThreshold.getValue()) {
                disconnect();
            }
        }
    }

    @Subscribe
    private void onKeyInput(KeyInputEvent event) {
        if (disconnectBind.getValue().isEmpty() || event.getAction() != 1) return;
        if (event.getKey() == disconnectBind.getValue().getKey()) {
            if (!nullCheck() && isSurvival()) disconnect();
        }
    }

    private void pollForRechamber(Channel channel, long deadline) {
        if (!channel.isOpen()) { awaitingRechamber = false; return; }
        channel.eventLoop().schedule(() -> mc.execute(() -> {
            if (!awaitingRechamber || nullCheck() || !isSurvival()) {
                awaitingRechamber = false;
                return;
            }
            updateTotemCount();
            boolean offhandHasTotem = mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
            boolean noTotems = cachedTotems == 0;
            boolean timedOut = System.currentTimeMillis() >= deadline;

            if (offhandHasTotem || noTotems || timedOut) {
                awaitingRechamber = false;
                if (logOnTotems.getValue() && cachedTotems <= totemThreshold.getValue()) {
                    disconnect();
                }
            } else {
                pollForRechamber(channel, deadline);
            }
        }), 10, TimeUnit.MILLISECONDS);
    }

    private void checkThresholds() {
        if (logOnHealth.getValue()) {
            float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            if (hp <= healthThreshold.getValue()) {
                disconnect();
                return;
            }
        }
        if (logOnTotems.getValue() && cachedTotems <= totemThreshold.getValue()) {
            disconnect();
        }
    }

    private void updateTotemCount() {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.TOTEM_OF_UNDYING)) count += stack.getCount();
        }
        ItemStack offhand = mc.player.getOffhandItem();
        if (offhand.is(Items.TOTEM_OF_UNDYING)) count += offhand.getCount();
        cachedTotems = count;
    }

    private Channel getChannel() {
        if (mc.getConnection() == null) return null;
        return ((dev.leonetic.mixin.network.ConnectionAccessor) mc.getConnection().getConnection()).getChannel();
    }

    private void closeChannel() {
        Channel channel = getChannel();
        if (channel != null) channel.close();
    }

    private boolean isOffhandModuleEnabled() {
        OffhandModule offhand = Homovore.moduleManager.getModuleByClass(OffhandModule.class);
        return offhand != null && offhand.isEnabled();
    }

    private boolean isSurvival() {
        return mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SURVIVAL;
    }

    private void disconnect() {
        if (!nullCheck() && mc.getConnection() != null) {
            BlockPos pos = mc.player.blockPosition();
            List<String> nearby = new ArrayList<>(nearbyPlayerNames);

            StringBuilder msg = new StringBuilder("AutoLog Disconnect");
            msg.append("\nTotems: ").append(cachedTotems);
            msg.append("\nCoords: ").append(pos.getX()).append(", ").append(pos.getY()).append(", ").append(pos.getZ());
            msg.append(nearby.isEmpty() ? "\nNearby: none" : "\nNearby: " + String.join(", ", nearby));

            mc.getConnection().getConnection().disconnect(Component.literal(msg.toString()));
        } else {
            closeChannel();
        }
        disable();
    }

    @Override
    public String getDisplayInfo() {
        if (nullCheck()) return null;
        return String.valueOf(cachedTotems);
    }
}
