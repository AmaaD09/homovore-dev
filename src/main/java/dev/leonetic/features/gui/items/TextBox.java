package dev.leonetic.features.gui.items;

import dev.leonetic.features.gui.GuiTheme;
import dev.leonetic.util.render.GuiFade;
import dev.leonetic.util.render.RenderUtil;
import dev.leonetic.util.render.font.Fonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

public class TextBox {
    private static final Minecraft mc = Minecraft.getInstance();
    private static TextBox ACTIVE;

    public static final IntPredicate ANY_PRINTABLE = c -> c >= 32;

    private final StringBuilder buffer = new StringBuilder();
    private String placeholder = "";
    private IntPredicate filter = ANY_PRINTABLE;
    private int maxLength = 256;
    private Consumer<String> onChange;
    private Runnable onCommit;
    private Runnable onCancel;

    public TextBox placeholder(String s) { this.placeholder = s == null ? "" : s; return this; }
    public TextBox filter(IntPredicate f) { this.filter = f == null ? ANY_PRINTABLE : f; return this; }
    public TextBox maxLength(int n) { this.maxLength = Math.max(0, n); return this; }
    public TextBox onChange(Consumer<String> c) { this.onChange = c; return this; }
    public TextBox onCommit(Runnable r) { this.onCommit = r; return this; }
    public TextBox onCancel(Runnable r) { this.onCancel = r; return this; }

    public String getText() { return buffer.toString(); }
    public boolean isEmpty() { return buffer.length() == 0; }

    public void setText(String s) {
        buffer.setLength(0);
        if (s != null) buffer.append(s);
        fireChange();
    }

    public void clear() {
        buffer.setLength(0);
        unfocus();
        fireChange();
    }

    public boolean isFocused() { return ACTIVE == this; }

    public static boolean hasActiveFocus() { return ACTIVE != null; }
    public static TextBox getActive() { return ACTIVE; }
    public static void unfocusAll() { ACTIVE = null; }
    public static void routeKeyPressed(int key) { if (ACTIVE != null) ACTIVE.keyPressed(key); }
    public static void routeKeyTyped(String typed) { if (ACTIVE != null) ACTIVE.keyTyped(typed); }

    public void focus() {
        if (ACTIVE == this) return;
        if (ACTIVE != null) ACTIVE.unfocus();
        ACTIVE = this;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    public void unfocus() {
        if (ACTIVE == this) ACTIVE = null;
    }

    private void fireChange() {
        if (onChange != null) onChange.accept(buffer.toString());
    }

    public void renderPill(GuiGraphics ctx, float x1, float y1, float x2, float y2,
                           Color accent, boolean hovering, float leftPad) {
        boolean active = isFocused() || buffer.length() > 0;
        if (active) {
            int wash = GuiTheme.withAlpha(accent, 90).getRGB();
            RenderUtil.rect(ctx, x1, y1, x2, y2, GuiTheme.BODY_BG);
            RenderUtil.rect(ctx, x1, y1, x2, y2, wash);
            RenderUtil.rect(ctx, x1, y1, x2, y1 + 1f, GuiTheme.HIGHLIGHT_TOP);
            int border = GuiTheme.brighten(accent, 0.25f).getRGB();
            RenderUtil.rect(ctx, x1, y1, x2, y2, border, 1f);
        } else {
            RenderUtil.rect(ctx, x1, y1, x2, y2, GuiTheme.BODY_BG);
            if (hovering) {
                RenderUtil.rect(ctx, x1, y1, x2, y2, GuiTheme.withAlpha(accent, 50).getRGB());
            }
            RenderUtil.rect(ctx, x1, y1, x2, y2, GuiTheme.OUTLINE_INNER, 1f);
        }

        float textX = x1 + leftPad;
        float textY = y1 + (y2 - y1 - 8f) / 2f;
        int textColor = isFocused() ? GuiTheme.TEXT_MODULE_ON : GuiTheme.TEXT_SETTING;
        if (buffer.length() == 0 && !isFocused()) {
            drawString(ctx, placeholder, textX, textY, GuiTheme.TEXT_SETTING_VALUE);
        } else {
            String shown = buffer.toString();
            drawString(ctx, shown, textX, textY, textColor);
            drawCaret(ctx, textX + Fonts.width(shown), textY, textColor);
        }
    }

    public float renderInlineRight(GuiGraphics ctx, float rightX, float centerY, int color) {
        String shown = buffer.toString();
        int w = Fonts.width(shown);
        float textX = rightX - w;
        float textY = centerY - 4f;
        drawString(ctx, shown, textX, textY, color);
        drawCaret(ctx, rightX, textY, color);
        return textX;
    }

    private void drawCaret(GuiGraphics ctx, float cx, float textY, int color) {
        if (!isFocused()) return;
        boolean blink = (System.currentTimeMillis() / 500L) % 2L == 0L;
        if (!blink) return;
        RenderUtil.rect(ctx, cx, textY - 1f, cx + 1f, textY + 8f, color);
    }

    public void keyPressed(int key) {
        if (!isFocused()) return;
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (onCancel != null) onCancel.run();
                unfocus();
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (onCommit != null) onCommit.run();
                unfocus();
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (buffer.length() > 0) {
                    buffer.deleteCharAt(buffer.length() - 1);
                    fireChange();
                }
            }
        }
    }

    public void keyTyped(String typed) {
        if (!isFocused() || typed == null || typed.isEmpty()) return;
        boolean changed = false;
        for (int i = 0; i < typed.length(); i++) {
            char c = typed.charAt(i);
            if (!filter.test(c)) continue;
            if (buffer.length() >= maxLength) break;
            buffer.append(c);
            changed = true;
        }
        if (changed) fireChange();
    }

    private void drawString(GuiGraphics ctx, String text, float x, float y, int color) {
        Fonts.drawString(ctx, text, x, y, GuiFade.apply(color));
    }
}
