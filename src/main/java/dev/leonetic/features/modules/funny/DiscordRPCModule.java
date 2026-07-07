package dev.leonetic.features.modules.funny;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import meteordevelopment.discordipc.DiscordIPC;
import meteordevelopment.discordipc.RichPresence;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import static java.lang.Math.abs;

public class DiscordRPCModule extends Module {
    private static final long APP_ID = 1497806002139955270L;

    public enum LineMode {
        CUSTOM, SERVER, DIMENSION, HEALTH, STATS
    }

    private final Setting<LineMode> line1Mode = mode("Line1Mode", LineMode.SERVER).setPage("General");
    private final Setting<String>   line1Custom = str("Line1Custom", "Owning on [server]").setPage("General");

    private final Setting<LineMode> line2Mode = mode("Line2Mode", LineMode.STATS).setPage("General");
    private final Setting<String>   line2Custom = str("Line2Custom", "HP: [health] | Ping: [ping]ms").setPage("General");

    private final Setting<Boolean>  hideIp = bool("HideIP", false).setPage("General");
    private final Setting<Integer>  antileak = num("AntiLeakRadius", 50000, 1000, 100000).setPage("General");
    
    private static final RichPresence rpc = new RichPresence();
    private int ticks;

    public DiscordRPCModule() {
        super("DiscordRPC", "Shows a highly customizable Discord Rich Presence.", Category.FUNNY);
    }

    @Override
    public void onEnable() {
        DiscordIPC.start(APP_ID, null);
        rpc.setStart(System.currentTimeMillis() / 1000L);
        rpc.setLargeImage("icon", "Homovore Client");
        ticks = 0;
    }

    @Override
    public void onDisable() {
        DiscordIPC.stop();
    }

    @Override
    public void onTick() {
        if (++ticks < 20) return;
        ticks = 0;

        if (nullCheck()) return;

        rpc.setDetails(formatLine(line1Mode.getValue(), line1Custom.getValue()));
        rpc.setState(formatLine(line2Mode.getValue(), line2Custom.getValue()));

        if (mc.player != null) {
            String name = mc.player.getName().getString();
            rpc.setSmallImage("https://minotar.net/helm/" + name + "/100.png", name);
        }

        DiscordIPC.setActivity(rpc);
    }

    private String formatLine(LineMode mode, String customFormat) {
        return switch (mode) {
            case SERVER -> "Playing on " + getServerIp();
            case DIMENSION -> getDimensionText() + " " + getCoordsText();
            case HEALTH -> "Health: " + getHealth() + " | Ping: " + getPing() + "ms";
            case STATS -> "Totems: " + getTotems() + " | Modules: " + getActiveModules();
            case CUSTOM -> applyTokens(customFormat);
        };
    }

    private String applyTokens(String text) {
        if (text == null) return "";
        return text.replace("[server]", getServerIp())
                   .replace("[ping]", String.valueOf(getPing()))
                   .replace("[health]", getHealth())
                   .replace("[dimension]", getDimensionText())
                   .replace("[coords]", getCoordsText())
                   .replace("[totems]", String.valueOf(getTotems()))
                   .replace("[modules]", String.valueOf(getActiveModules()));
    }

    private String getServerIp() {
        if (mc.hasSingleplayerServer()) return "Singleplayer";
        if (mc.getCurrentServer() != null) {
            return hideIp.getValue() ? "Private Server" : mc.getCurrentServer().ip;
        }
        return "Main Menu";
    }

    private String getDimensionText() {
        if (mc.player == null) return "Unknown";
        String dimPath = mc.player.level().dimension().identifier().getPath();
        return switch (dimPath) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "The Nether";
            case "the_end" -> "The End";
            default -> dimPath;
        };
    }

    private String getCoordsText() {
        if (mc.player == null) return "";
        int radius = antileak.getValue();
        if (abs(mc.player.getBlockX()) > radius || abs(mc.player.getBlockZ()) > radius) {
            return "[x, y, z]";
        } else {
            return String.format("[%d, %d, %d]",
                    mc.player.getBlockX(),
                    mc.player.getBlockY(),
                    mc.player.getBlockZ());
        }
    }

    private String getHealth() {
        if (mc.player == null) return "0.0";
        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        return String.format("%.1f", hp);
    }

    private int getPing() {
        if (mc.player == null || mc.getConnection() == null) return 0;
        var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : info.getLatency();
    }

    private int getTotems() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private int getActiveModules() {
        return (int) Homovore.moduleManager.getModules().stream().filter(Module::isEnabled).count();
    }
}
