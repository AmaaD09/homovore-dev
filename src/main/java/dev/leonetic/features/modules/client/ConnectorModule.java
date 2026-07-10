package dev.leonetic.features.modules.client;

import dev.leonetic.Homovore;
import dev.leonetic.features.commands.Command;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

import java.util.Locale;

/**
 * Connector.
 *
 * <p>Dials a specific IP while still announcing the original hostname in the
 * handshake. Servers behind TCPShield (2b2t and friends) route by the handshake
 * host, so this lets you pin a specific edge IP — e.g. one you measured to have
 * the lowest ping — instead of whichever edge DNS happens to hand you, without
 * TCPShield rejecting the connection.</p>
 *
 * <p>No scanning: you pick the IP. Two hooks do the work:</p>
 * <ul>
 *   <li>{@link #rewriteAddress} swaps the dialed {@link ServerAddress} from the
 *       hostname to the configured IP when the host matches.</li>
 *   <li>{@link #rewriteHandshakeHost} restores the original hostname inside the
 *       outgoing handshake so the edge forwards to the real backend.</li>
 * </ul>
 */
public class ConnectorModule extends Module {
    private static final long HANDSHAKE_OVERRIDE_TTL_MS = 15000L;

    private static ConnectorModule INSTANCE;

    private final Setting<String>  host    = str("Host", "2b2t.org");
    private final Setting<String>  ip      = str("IP", "40.223.14.1");
    private final Setting<Integer> port    = num("Port", 25565, 1, 65535);
    private final Setting<Boolean> notify  = bool("Notify", true);

    private volatile HandshakeOverride pendingHandshakeOverride;

    public ConnectorModule() {
        super("Connector", "Connects to a chosen IP while presenting the real hostname, pinning a specific server edge.", Category.CLIENT);
        INSTANCE = this;
    }

    public static ConnectorModule getInstance() {
        if (INSTANCE != null) return INSTANCE;
        return Homovore.moduleManager != null ? Homovore.moduleManager.getModuleByClass(ConnectorModule.class) : null;
    }

    @Override
    public void onDisable() {
        pendingHandshakeOverride = null;
    }

    @Override
    public String getDisplayInfo() {
        return isEnabled() ? ip.getValue().trim() : null;
    }

    /**
     * Called from the connect-screen mixin with the address the client is about to
     * dial. If enabled and the host matches the configured target, returns a new
     * {@link ServerAddress} pointed at the configured IP; otherwise unchanged.
     */
    public ServerAddress rewriteAddress(ServerAddress originalAddress) {
        if (!isEnabled() || originalAddress == null) return originalAddress;

        String target = host.getValue().trim();
        String rawIp = ip.getValue().trim();
        if (target.isEmpty() || rawIp.isEmpty()) return originalAddress;
        if (!originalAddress.getHost().equalsIgnoreCase(target)) return originalAddress;
        if (rawIp.equalsIgnoreCase(originalAddress.getHost())) return originalAddress;

        this.pendingHandshakeOverride = new HandshakeOverride(
                rawIp, originalAddress.getHost(), System.currentTimeMillis() + HANDSHAKE_OVERRIDE_TTL_MS);
        if (notify.getValue()) {
            notifyUser("Connecting to %s as %s.", rawIp, originalAddress.getHost());
        }
        return new ServerAddress(rawIp, port.getValue());
    }

    /**
     * Called from the connection mixin with the host string written into the
     * handshake packet. When a rewrite is pending and the handshake host equals the
     * IP we dialed, swaps it back to the original hostname so the edge forwards to
     * the real backend. One-shot per rewrite.
     */
    public String rewriteHandshakeHost(String handshakeHost) {
        if (!isEnabled() || handshakeHost == null) return handshakeHost;
        HandshakeOverride override = this.pendingHandshakeOverride;
        if (override == null) return handshakeHost;

        long now = System.currentTimeMillis();
        if (now > override.expiresAtMs()) {
            if (this.pendingHandshakeOverride == override) {
                this.pendingHandshakeOverride = null;
            }
            return handshakeHost;
        }
        if (!handshakeHost.equalsIgnoreCase(override.resolvedHost())) {
            return handshakeHost;
        }
        this.pendingHandshakeOverride = null;
        return override.originalHost();
    }

    private void notifyUser(String format, Object... args) {
        String message = String.format(Locale.ROOT, format, args);
        if (mc != null) {
            mc.execute(() -> {
                if (mc.player != null) {
                    Command.sendMessage(message);
                } else {
                    Homovore.LOGGER.info("[Connector] {}", message);
                }
            });
        } else {
            Homovore.LOGGER.info("[Connector] {}", message);
        }
    }

    private record HandshakeOverride(String resolvedHost, String originalHost, long expiresAtMs) {
    }
}
