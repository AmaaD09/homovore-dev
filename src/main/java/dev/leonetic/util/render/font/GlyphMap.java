package dev.leonetic.util.render.font;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import it.unimi.dsi.fastutil.chars.Char2ObjectArrayMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class GlyphMap {

    private static final AtomicInteger ID_COUNTER = new AtomicInteger();

    private final Font[] fonts;
    private final char include;
    private final char exclude;
    private final int padding;
    private final Char2ObjectArrayMap<Glyph> glyphs = new Char2ObjectArrayMap<>();

    private boolean generated;
    private DynamicTexture texture;
    private Identifier textureId;
    private int width;
    private int height;

    public GlyphMap(Font[] fonts, char include, char exclude, int padding) {
        this.fonts = fonts;
        this.include = include;
        this.exclude = exclude;
        this.padding = padding;
    }

    public void generate() {
        synchronized (this) { generateInternal(); }
    }

    public Glyph getGlyph(char character) {
        synchronized (this) {
            if (!generated) generateInternal();
            return glyphs.get(character);
        }
    }

    public boolean contains(char c) {
        return c >= include && c < exclude;
    }

    public void destroy() {
        synchronized (this) {
            generated = false;
            if (texture != null) {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && textureId != null) mc.getTextureManager().release(textureId);
                texture.close();
                texture = null;
                textureId = null;
            }
            glyphs.clear();
            width = -1;
            height = -1;
        }
    }

    public Identifier getTextureId() { return textureId; }
    public int        getWidth()     { return width; }
    public int        getHeight()    { return height; }

    private void generateInternal() {
        if (generated) return;

        int count = exclude - include;
        if (count <= 0) {
            width = 1; height = 1; generated = true; return;
        }

        FontRenderContext frc = new FontRenderContext(new AffineTransform(), true, true);

        int[] gw = new int[count];
        int[] gh = new int[count];
        float[] adv = new float[count];
        char[] chars = new char[count];

        for (int i = 0; i < count; i++) {
            char c = (char) (include + i);
            chars[i] = c;
            Font font = getFontForGlyph(c);
            Rectangle2D b = font.getStringBounds(String.valueOf(c), frc);
            adv[i] = (float) b.getWidth();
            gw[i] = (int) Math.ceil(adv[i]);
            gh[i] = (int) Math.ceil(b.getHeight());
        }

        int colsTarget = (int) Math.ceil(Math.sqrt(count) * 1.5);
        int[] ux = new int[count];
        int[] uy = new int[count];
        int curX = 0, curY = 0, rowH = 0, maxX = 0, col = 0;
        for (int i = 0; i < count; i++) {
            int w = Math.max(gw[i], 1);
            int h = Math.max(gh[i], 1);
            if (col >= colsTarget) {
                curX = 0;
                curY += rowH + padding;
                rowH = 0;
                col = 0;
            }
            ux[i] = curX;
            uy[i] = curY;
            curX += w + padding;
            if (curX > maxX) maxX = curX;
            if (h > rowH) rowH = h;
            col++;
        }
        int atlasW = Math.max(maxX + padding, 1);
        int atlasH = Math.max(curY + rowH + padding, 1);

        BufferedImage img = new BufferedImage(atlasW, atlasH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.setColor(new Color(0, 0, 0, 0));
        g.fillRect(0, 0, atlasW, atlasH);
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(Color.WHITE);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        for (int i = 0; i < count; i++) {
            if (gw[i] > 0 && gh[i] > 0) {
                Font font = getFontForGlyph(chars[i]);
                g.setFont(font);
                FontMetrics fm = g.getFontMetrics();
                g.drawString(String.valueOf(chars[i]), ux[i], uy[i] + fm.getAscent());
                glyphs.put(chars[i], new Glyph(this, ux[i], uy[i], gw[i], gh[i], adv[i], chars[i]));
            } else {
                glyphs.put(chars[i], new Glyph(this, 0, 0, 0, 0, adv[i], chars[i]));
            }
        }
        g.dispose();

        width = atlasW;
        height = atlasH;

        int[] rgb = new int[atlasW * atlasH];
        img.getRGB(0, 0, atlasW, atlasH, rgb, 0, atlasW);

        NativeImage native_ = new NativeImage(NativeImage.Format.RGBA, atlasW, atlasH, true);
        IntBuffer buf = MemoryUtil.memIntBuffer(native_.getPointer(), atlasW * atlasH);
        for (int i = 0; i < rgb.length; i++) {
            int p = rgb[i];
            int a = (p >>> 24) & 0xFF;
            int r = (p >>> 16) & 0xFF;
            int g2 = (p >>> 8) & 0xFF;
            int b = p & 0xFF;
            buf.put((a << 24) | (b << 16) | (g2 << 8) | r);
        }

        int id = ID_COUNTER.getAndIncrement();
        texture = new LinearDynamicTexture(() -> "HomovoreFontPage" + id, native_);
        texture.upload();

        textureId = Identifier.fromNamespaceAndPath("homovore", "font/page_" + id);
        Minecraft.getInstance().getTextureManager().register(textureId, texture);

        generated = true;
    }

    private static final class LinearDynamicTexture extends DynamicTexture {
        LinearDynamicTexture(Supplier<String> label, NativeImage image) {
            super(label, image);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }

    private Font getFontForGlyph(char c) {
        for (Font font : fonts) {
            if (font.canDisplay(c)) return font;
        }
        return fonts[0];
    }

    public record Glyph(GlyphMap parent, int u, int v, int width, int height,
                        float advance, char value) {}
}
