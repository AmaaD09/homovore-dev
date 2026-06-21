package dev.leonetic.util.render.font;

import dev.leonetic.features.modules.client.ClickGuiModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

import java.awt.Font;
import java.io.InputStream;

public final class Fonts {

    private Fonts() {}

    private static final Minecraft mc = Minecraft.getInstance();
    private static final String BUNDLED_PATH = "/assets/homovore/font/default.ttf";

    private static final float SHADOW_OFFSET = 0.7f;

    private static volatile FontRenderer renderer;
    private static volatile boolean dirty = true;
    private static volatile boolean hudPass = false;

    private static Font bundledBase;
    private static boolean bundledLoaded;

    private static ClickGuiModule cfg() { return ClickGuiModule.getInstance(); }

    private static boolean clickGuiOn() { ClickGuiModule c = cfg(); return c != null && c.clickGuiFont.getValue(); }
    private static boolean hudOn()      { ClickGuiModule c = cfg(); return c != null && c.hudFont.getValue(); }
    private static boolean globalOn()   { ClickGuiModule c = cfg(); return c != null && c.minecraftFont.getValue(); }
    private static boolean anyOn()      { return clickGuiOn() || hudOn() || globalOn(); }

    public static void markDirty() { dirty = true; }

    public static void beginHudPass() { hudPass = true; }
    public static void endHudPass()   { hudPass = false; }

    public static void drawString(GuiGraphics graphics, String text, float x, float y, int color) {
        FontRenderer fr = clickGuiOn() ? renderer() : null;
        if (fr == null) {
            graphics.drawString(mc.font, text, (int) x, (int) y, color);
            return;
        }
        fr.drawString(graphics, text, x + SHADOW_OFFSET, y + SHADOW_OFFSET, color, true);
        fr.drawString(graphics, text, x, y, color, false);
    }

    public static int width(String text) {
        FontRenderer fr = clickGuiOn() ? renderer() : null;
        return fr == null ? mc.font.width(text) : Math.round(fr.getTextWidth(text));
    }

    public static int lineHeight() {
        FontRenderer fr = clickGuiOn() ? renderer() : null;
        return fr == null ? mc.font.lineHeight : Math.max(1, Math.round(fr.getHeight()));
    }

    public static boolean drawOverrideActive() {
        return (globalOn() || (hudPass && hudOn())) && renderer() != null;
    }

    public static void renderOverrideString(GuiGraphics graphics, String text, float x, float y, int color, boolean shadow) {
        FontRenderer fr = renderer();
        if (fr == null) return;
        if (shadow) fr.drawString(graphics, text, x + SHADOW_OFFSET, y + SHADOW_OFFSET, color, true);
        fr.drawString(graphics, text, x, y, color, false);
    }

    public static void renderOverrideText(GuiGraphics graphics, FormattedCharSequence text, float x, float y, int color, boolean shadow) {
        FontRenderer fr = renderer();
        if (fr == null) return;
        if (shadow) fr.drawText(graphics, text, x + SHADOW_OFFSET, y + SHADOW_OFFSET, color, true);
        fr.drawText(graphics, text, x, y, color, false);
    }

    public static int overrideWidth(String text) {
        FontRenderer fr = renderer();
        return fr == null ? mc.font.width(text) : Math.round(fr.getTextWidth(text));
    }

    public static int overrideWidth(FormattedCharSequence text) {
        FontRenderer fr = renderer();
        return fr == null ? mc.font.width(text) : Math.round(fr.getTextWidth(text));
    }

    private static FontRenderer renderer() {
        if (!anyOn()) {
            disposeIfPresent();
            return null;
        }
        if (mc.getWindow() == null) return null;
        if (dirty || renderer == null) rebuild();
        return renderer;
    }

    private static synchronized void rebuild() {
        if (!dirty && renderer != null) return;
        disposeIfPresent();

        ClickGuiModule c = cfg();
        Font base = resolveBaseFont(c == null ? "" : c.fontName.getValue());
        renderer = new FontRenderer(new Font[]{base});
        dirty = false;
    }

    private static synchronized void disposeIfPresent() {
        FontRenderer old = renderer;
        renderer = null;
        if (old != null) old.close();
    }

    private static Font resolveBaseFont(String familyName) {
        if (familyName != null && !familyName.isBlank()) {

            return new Font(familyName.trim(), Font.PLAIN, 1);
        }
        Font bundled = bundled();
        if (bundled != null) return bundled;
        return new Font(Font.SANS_SERIF, Font.PLAIN, 1);
    }

    private static Font bundled() {
        if (bundledLoaded) return bundledBase;
        bundledLoaded = true;
        try (InputStream is = Fonts.class.getResourceAsStream(BUNDLED_PATH)) {
            if (is != null) {
                bundledBase = Font.createFont(Font.TRUETYPE_FONT, is).deriveFont(1f);
            }
        } catch (Exception e) {
            bundledBase = null;
        }
        return bundledBase;
    }
}
