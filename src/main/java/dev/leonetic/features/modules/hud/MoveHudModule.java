package dev.leonetic.features.modules.hud;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.event.impl.render.Render2DEvent;
import dev.leonetic.features.modules.client.HudClientModule;
import dev.leonetic.features.modules.client.HudModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class MoveHudModule extends HudModule {
    private static final int LEFT_MARGIN = 2;
    private static final int BOTTOM_MARGIN = 2;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;

    private static final long WINDOW_MS = 1000;
    private static final int EXPECTED_PER_SECOND = 20;

    private long windowStart = System.currentTimeMillis();
    private int windowCount = 0;
    private int lastCount = 0;

    public MoveHudModule() {
        super("Move");
        EVENT_BUS.register(this);
    }

    @Subscribe
    private void onPacketSend(PacketEvent.Send event) {
        if (!(event.getPacket() instanceof ServerboundMovePlayerPacket)) return;
        rollWindow();
        windowCount++;
    }

    private void rollWindow() {
        long now = System.currentTimeMillis();
        if (now - windowStart < WINDOW_MS) return;

        lastCount = windowCount;
        windowCount = 0;
        windowStart = now;
    }

    @Override
    public void render(Render2DEvent event) {
        rollWindow();

        GuiGraphics ctx = event.getContext();
        String value = String.valueOf(lastCount);
        String total = "/" + EXPECTED_PER_SECOND;

        int ry = bottomAnchor() - BOTTOM_MARGIN - mc.font.lineHeight;

        HudClientModule hud = Homovore.moduleManager.getModuleByClass(HudClientModule.class);
        if (hud != null && hud.isElementEnabled(SpeedHudModule.class)) {
            ry -= mc.font.lineHeight;
        }

        ctx.drawString(mc.font, value, LEFT_MARGIN, ry, GRAY);
        ctx.drawString(mc.font, total, LEFT_MARGIN + mc.font.width(value), ry, WHITE);
    }
}
