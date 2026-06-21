package dev.leonetic.features.gui.items;

import dev.leonetic.features.Feature;
import dev.leonetic.util.render.GuiFade;
import dev.leonetic.util.render.font.Fonts;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.*;

public class Item
        extends Feature {
    public static GuiGraphics context;
    protected float x;
    protected float y;
    protected int width;
    protected int height;
    private boolean hidden;

    public Item(String name) {
        super(name);
    }

    public void setLocation(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void drawScreen(GuiGraphics context, int mouseX, int mouseY, float partialTicks) {
    }

    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
    }

    public void mouseReleased(int mouseX, int mouseY, int releaseButton) {
    }

    public void update() {
    }

    public void onKeyTyped(String typedChar, int keyCode) {
    }

    public void onKeyPressed(int key) {
    }

    public void keyActivate() {
    }

    public int getRowHeight() {
        return getHeight();
    }

    public float getX() {
        return this.x;
    }

    public float getY() {
        return this.y;
    }

    public int getWidth() {
        return this.width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return this.height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public boolean isHidden() {
        return this.hidden;
    }

    public boolean setHidden(boolean hidden) {
        this.hidden = hidden;
        return this.hidden;
    }

    private long scrollStartTime = 0;

    protected void drawScrollableString(String text, double x, double y, int color, int maxWidth, boolean isHovering) {
        int textWidth = Fonts.width(text);
        if (textWidth <= maxWidth) {
            scrollStartTime = 0;
            drawString(text, x, y, color);
            return;
        }
        if (!isHovering) {
            scrollStartTime = 0;
            drawString(truncateText(text, maxWidth), x, y, color);
            return;
        }
        if (scrollStartTime == 0) scrollStartTime = System.currentTimeMillis();
        long elapsed = System.currentTimeMillis() - scrollStartTime;
        float overflow = textWidth - maxWidth;
        long scrollDurationMs = (long) (overflow / 0.03f);
        long pause = 600L;
        long cycle = pause + scrollDurationMs + pause + scrollDurationMs;
        long t = elapsed % cycle;
        float offset;
        if (t < pause) {
            offset = 0;
        } else if (t < pause + scrollDurationMs) {
            offset = (t - pause) * 0.03f;
        } else if (t < pause + scrollDurationMs + pause) {
            offset = overflow;
        } else {
            offset = overflow - (t - pause - scrollDurationMs - pause) * 0.03f;
        }
        context.enableScissor((int) x, (int) y - 2, (int) (x + maxWidth + 2), (int) (y + Fonts.lineHeight() + 2));
        drawString(text, x - offset, y, color);
        context.disableScissor();
    }

    private String truncateText(String text, int maxWidth) {
        String ellipsis = "...";
        int ellipsisWidth = Fonts.width(ellipsis);
        if (maxWidth <= ellipsisWidth) return ellipsis;
        int targetWidth = maxWidth - ellipsisWidth;
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Fonts.width(sb.toString() + c) > targetWidth) break;
            sb.append(c);
        }
        return sb + ellipsis;
    }

    protected void drawString(String text, double x, double y, Color color) {
        drawString(text, x, y, color.hashCode());
    }

    protected void drawString(String text, double x, double y, int color) {
        Fonts.drawString(context, text, (float) x, (float) y, GuiFade.apply(color));
    }

    public boolean isHovering(int mouseX, int mouseY) {
        return false;
    }

    public String getPage() {
        return "General";
    }
}
