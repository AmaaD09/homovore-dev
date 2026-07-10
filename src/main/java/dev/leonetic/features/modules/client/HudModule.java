package dev.leonetic.features.modules.client;

import dev.leonetic.event.impl.render.Render2DEvent;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.traits.Util;
import net.minecraft.client.gui.screens.ChatScreen;
import org.joml.Vector2f;

public abstract class HudModule implements Util {
    private static final int CHAT_INPUT_HEIGHT = 14;

    private final String name;

    private Setting<Vector2f> offset;

    private int boxX, boxY, boxW, boxH;
    private boolean boundsThisFrame;
    private boolean boundsLastFrame;

    public HudModule(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public abstract void render(Render2DEvent event);

    public void setOffsetSetting(Setting<Vector2f> offset) {
        this.offset = offset;
    }

    public int offsetX() {
        return offset == null ? 0 : Math.round(offset.getValue().x());
    }

    public int offsetY() {
        return offset == null ? 0 : Math.round(offset.getValue().y());
    }

    public void setOffset(int x, int y) {
        if (offset != null) offset.setValue(new Vector2f(x, y));
    }

    public void resetOffset() {
        setOffset(0, 0);
    }

    public void beginBounds() {
        boundsThisFrame = false;
    }

    protected void mark(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        int x2 = x + w, y2 = y + h;
        if (!boundsThisFrame) {
            boxX = x; boxY = y; boxW = w; boxH = h;
            boundsThisFrame = true;
        } else {
            int nx = Math.min(boxX, x);
            int ny = Math.min(boxY, y);
            int nx2 = Math.max(boxX + boxW, x2);
            int ny2 = Math.max(boxY + boxH, y2);
            boxX = nx; boxY = ny; boxW = nx2 - nx; boxH = ny2 - ny;
        }
    }

    public void endBounds() {
        boundsLastFrame = boundsThisFrame;
    }

    public boolean hasBounds() {
        return boundsLastFrame;
    }

    public int boxX() { return boxX; }
    public int boxY() { return boxY; }
    public int boxW() { return boxW; }
    public int boxH() { return boxH; }

    protected int screenWidth() {
        return mc.getWindow().getGuiScaledWidth();
    }

    protected int screenHeight() {
        return mc.getWindow().getGuiScaledHeight();
    }

    protected int chatOffset() {
        return mc.screen instanceof ChatScreen ? CHAT_INPUT_HEIGHT : 0;
    }

    protected int bottomAnchor() {
        return screenHeight() - chatOffset();
    }
}
