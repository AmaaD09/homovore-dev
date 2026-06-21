package dev.leonetic.util.render.font;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FontRenderer implements Closeable {

    private static final Minecraft mc = Minecraft.getInstance();

    private static final float REF_TEXT_HEIGHT = 8f;
    private static final float TARGET_ASCENT = 7f;

    private final Map<Identifier, ObjectList<DrawEntry>> glyphPages = new HashMap<>();
    private final ObjectList<GlyphMap> maps = new ObjectArrayList<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private final int charsPerPage;
    private final int padding;

    private Font[] baseFonts;
    private Font[] fittedFonts;
    private int multiplier;
    private int previousGameScale = -1;
    private int blockPx;
    private boolean initialized;

    public FontRenderer(Font[] fonts) {
        this(fonts, 256, 4);
    }

    public FontRenderer(Font[] fonts, int charactersPerPage, int paddingBetweenCharacters) {
        if (fonts.length == 0) throw new IllegalArgumentException("fonts.length == 0");
        if (charactersPerPage <= 4) throw new IllegalArgumentException("charactersPerPage too small");
        if (paddingBetweenCharacters < 0) throw new IllegalArgumentException("padding < 0");

        this.baseFonts = fonts;
        this.charsPerPage = charactersPerPage;
        this.padding = paddingBetweenCharacters;
        init();
    }

    public void drawString(GuiGraphics graphics, String text, float x, float y, int color, boolean dropShadow) {
        drawText(graphics, parse(text, color).getVisualOrderText(), x, y, color, dropShadow);
    }

    public void drawText(GuiGraphics graphics, FormattedCharSequence text, float x, float y, int color, boolean dropShadow) {
        rebuildIfScaleChanged();

        if ((color & 0xFC000000) == 0) color |= 0xFF000000;
        int alpha = (color >>> 24) & 0xFF;
        int baseRgb = dropShadow ? shadow(color) : (color & 0x00FFFFFF);

        float[] pen = { 0f };

        synchronized (glyphPages) {
            text.accept((index, style, codePoint) -> {
                int rgb;
                if (style.getColor() != null) {
                    int c = style.getColor().getValue();
                    rgb = dropShadow ? shadow(c) : (c & 0x00FFFFFF);
                } else {
                    rgb = baseRgb;
                }
                int argb = (alpha << 24) | rgb;

                for (char ch : Character.toChars(codePoint)) {
                    GlyphMap.Glyph glyph = locateGlyph(ch);
                    if (glyph == null) continue;
                    if (glyph.width() > 0 && glyph.height() > 0 && ch != ' ') {
                        Identifier pageId = glyph.parent().getTextureId();
                        glyphPages.computeIfAbsent(pageId, k -> new ObjectArrayList<>())
                                .add(new DrawEntry(pen[0], 0, argb, glyph));
                    }
                    pen[0] += glyph.advance();
                }
                return true;
            });

            float blockGui = blockPx / (float) multiplier;
            float yTop = y + (REF_TEXT_HEIGHT - blockGui) / 2f;

            var pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(x, yTop);
            float scale = 1.0f / this.multiplier;
            pose.scale(scale, scale);

            for (Map.Entry<Identifier, ObjectList<DrawEntry>> entry : glyphPages.entrySet()) {
                Identifier pageId = entry.getKey();
                List<DrawEntry> entries = entry.getValue();
                for (DrawEntry de : entries) {
                    GlyphMap.Glyph g = de.glyph;
                    GlyphMap parent = g.parent();
                    graphics.blit(RenderPipelines.GUI_TEXTURED, pageId,
                            Math.round(de.dx), Math.round(de.dy),
                            (float) g.u(), (float) g.v(),
                            g.width(), g.height(),
                            parent.getWidth(), parent.getHeight(),
                            de.argb);
                }
            }

            pose.popMatrix();
            glyphPages.clear();
        }
    }

    public static String stripControlCodes(String text) {
        if (text.indexOf('§') < 0) return text;
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) { i++; continue; }
            sb.append(c);
        }
        return sb.toString();
    }

    public float getTextWidth(String text) {
        rebuildIfScaleChanged();
        String stripped = stripControlCodes(text);
        float adv = 0f;
        for (int i = 0; i < stripped.length(); i++) {
            GlyphMap.Glyph g = locateGlyph(stripped.charAt(i));
            if (g != null) adv += g.advance();
        }
        return adv / multiplier;
    }

    public float getTextWidth(FormattedCharSequence text) {
        rebuildIfScaleChanged();
        float[] adv = { 0f };
        text.accept((index, style, codePoint) -> {
            for (char ch : Character.toChars(codePoint)) {
                GlyphMap.Glyph g = locateGlyph(ch);
                if (g != null) adv[0] += g.advance();
            }
            return true;
        });
        return adv[0] / multiplier;
    }

    public float getHeight() {
        return mc.font.lineHeight;
    }

    private void rebuildIfScaleChanged() {
        int current = (int) Math.round(mc.getWindow().getGuiScale());
        if (current != previousGameScale) {
            close();
            init();
        }
    }

    private void init() {
        if (initialized) throw new IllegalStateException("Double call to init()");
        lock.writeLock().lock();
        try {
            int scale = (int) Math.round(mc.getWindow().getGuiScale());
            if (scale < 1) scale = 1;
            this.previousGameScale = scale;
            this.multiplier = scale;

            BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D pg = probe.createGraphics();

            float probeSize = 16f * this.multiplier;
            pg.setFont(baseFonts[0].deriveFont(probeSize));
            FontMetrics probeFm = pg.getFontMetrics();
            float ascentRatio = probeFm.getAscent() / probeSize;
            float fitSize = (TARGET_ASCENT * this.multiplier) / ascentRatio;

            Font[] fitted = new Font[baseFonts.length];
            for (int i = 0; i < baseFonts.length; i++) {
                fitted[i] = baseFonts[i].deriveFont(fitSize);
            }

            pg.setFont(fitted[0]);
            FontMetrics fm = pg.getFontMetrics();
            this.blockPx = fm.getAscent() + fm.getDescent();
            pg.dispose();

            this.fittedFonts = fitted;
            initialized = true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private GlyphMap.Glyph locateGlyph(char ch) {
        lock.readLock().lock();
        try {
            for (GlyphMap m : maps) {
                if (m.contains(ch)) return m.getGlyph(ch);
            }
        } finally {
            lock.readLock().unlock();
        }
        int base = charsPerPage * (ch / charsPerPage);
        int top = Math.min(base + charsPerPage, Character.MAX_VALUE + 1);
        GlyphMap map = new GlyphMap(this.fittedFonts, (char) base, (char) top, padding);
        lock.writeLock().lock();
        try {
            map.generate();
            maps.add(map);
        } finally {
            lock.writeLock().unlock();
        }
        return map.getGlyph(ch);
    }

    @Override
    public void close() {
        lock.writeLock().lock();
        try {
            for (GlyphMap m : maps) m.destroy();
            maps.clear();
            initialized = false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private static int shadow(int rgb) {
        return (rgb & 0x00FCFCFC) >> 2;
    }

    private static Component parse(String text, int defaultColor) {
        if (text.indexOf('§') < 0) {
            return Component.literal(text).withStyle(Style.EMPTY.withColor(defaultColor));
        }
        MutableComponent root = Component.literal("").withStyle(Style.EMPTY.withColor(defaultColor));
        Style style = Style.EMPTY.withColor(defaultColor);
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                if (run.length() > 0) {
                    root.append(Component.literal(run.toString()).withStyle(style));
                    run.setLength(0);
                }
                char code = Character.toLowerCase(text.charAt(++i));
                style = applyCode(style, code, defaultColor);
            } else {
                run.append(c);
            }
        }
        if (run.length() > 0) root.append(Component.literal(run.toString()).withStyle(style));
        return root;
    }

    private static Style applyCode(Style s, char code, int defaultColor) {
        return switch (code) {
            case '0' -> s.withColor(0xFF000000);
            case '1' -> s.withColor(0xFF0000AA);
            case '2' -> s.withColor(0xFF00AA00);
            case '3' -> s.withColor(0xFF00AAAA);
            case '4' -> s.withColor(0xFFAA0000);
            case '5' -> s.withColor(0xFFAA00AA);
            case '6' -> s.withColor(0xFFFFAA00);
            case '7' -> s.withColor(0xFFAAAAAA);
            case '8' -> s.withColor(0xFF555555);
            case '9' -> s.withColor(0xFF5555FF);
            case 'a' -> s.withColor(0xFF55FF55);
            case 'b' -> s.withColor(0xFF55FFFF);
            case 'c' -> s.withColor(0xFFFF5555);
            case 'd' -> s.withColor(0xFFFF55FF);
            case 'e' -> s.withColor(0xFFFFFF55);
            case 'f' -> s.withColor(0xFFFFFFFF);
            case 'r' -> Style.EMPTY.withColor(defaultColor);
            default  -> s;
        };
    }

    private record DrawEntry(float dx, float dy, int argb, GlyphMap.Glyph glyph) {}
}
