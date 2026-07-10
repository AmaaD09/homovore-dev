package dev.leonetic.features.gui;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.client.HudClientModule;
import dev.leonetic.features.modules.client.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class HudEditor extends Screen {
    private static final int SNAP = 6;

    private static final int DIM       = 0x66000000;
    private static final int BOX       = 0x55FFFFFF;
    private static final int BOX_HOVER = 0xAAFFFFFF;
    private static final int BOX_DRAG  = 0xFF55AAFF;
    private static final int GUIDE     = 0xCC55AAFF;
    private static final int LABEL     = 0xFFFFFFFF;

    private final Minecraft client = Minecraft.getInstance();

    private HudModule dragging;
    private int grabDX, grabDY;
    private HudModule hovered;

    public HudEditor() {
        super(Component.literal("HUD Editor"));
    }

    private HudClientModule hud() {
        return Homovore.moduleManager.getModuleByClass(HudClientModule.class);
    }

    private List<HudModule> elements() {
        List<HudModule> out = new ArrayList<>();
        HudClientModule hud = hud();
        if (hud == null) return out;
        for (HudModule el : hud.getElements()) {
            if (hud.isEnabled(el) && el.hasBounds()) out.add(el);
        }
        return out;
    }

    private static int screenX(HudModule el) { return el.boxX() + el.offsetX(); }
    private static int screenY(HudModule el) { return el.boxY() + el.offsetY(); }

    private boolean contains(HudModule el, int mx, int my) {
        int x0 = screenX(el), y0 = screenY(el);
        return mx >= x0 && mx <= x0 + el.boxW() && my >= y0 && my <= y0 + el.boxH();
    }

    private HudModule topAt(List<HudModule> elements, int mx, int my) {
        for (int i = elements.size() - 1; i >= 0; i--) {
            HudModule el = elements.get(i);
            if (contains(el, mx, my)) return el;
        }
        return null;
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, DIM);

        List<HudModule> elements = elements();

        int guideX = Integer.MIN_VALUE, guideY = Integer.MIN_VALUE;
        if (dragging != null && dragging.hasBounds() && elements.contains(dragging)) {
            int w = dragging.boxW(), h = dragging.boxH();
            int desiredX = clamp(mouseX - grabDX, 0, this.width - w);
            int desiredY = clamp(mouseY - grabDY, 0, this.height - h);

            int[] sx = snap(desiredX, w, xTargets(elements, dragging));
            int[] sy = snap(desiredY, h, yTargets(elements, dragging));
            guideX = sx[1];
            guideY = sy[1];

            dragging.setOffset(sx[0] - dragging.boxX(), sy[0] - dragging.boxY());
        } else {
            dragging = null;
        }

        hovered = dragging != null ? dragging : topAt(elements, mouseX, mouseY);

        for (HudModule el : elements) {
            int x0 = screenX(el), y0 = screenY(el);
            int w = el.boxW(), h = el.boxH();
            int color = el == dragging ? BOX_DRAG : (el == hovered ? BOX_HOVER : BOX);
            outline(ctx, x0 - 1, y0 - 1, x0 + w + 1, y0 + h + 1, color);
            if (el == hovered) {
                int ly = y0 - client.font.lineHeight - 1;
                if (ly < 0) ly = y0 + h + 1;
                ctx.drawString(client.font, el.getName(), x0, ly, LABEL);
            }
        }

        if (guideX != Integer.MIN_VALUE) ctx.fill(guideX, 0, guideX + 1, this.height, GUIDE);
        if (guideY != Integer.MIN_VALUE) ctx.fill(0, guideY, this.width, guideY + 1, GUIDE);

        String help = "Drag to move, Right click to reset, ESC to finish";
        ctx.drawString(client.font, help, 4, this.height - client.font.lineHeight - 4, LABEL);
        if (elements.isEmpty()) {
            String none = "No visible HUD elements. Enable some in the hud module";
            ctx.drawString(client.font, none, 4, 4, LABEL);
        }
    }

    private int[] snap(int topLeft, int size, int[] targets) {
        int best = SNAP + 1;
        int snapped = topLeft;
        int guide = Integer.MIN_VALUE;
        int[] anchors = { 0, size / 2, size };
        for (int t : targets) {
            for (int a : anchors) {
                int d = Math.abs(topLeft + a - t);
                if (d < best) {
                    best = d;
                    snapped = t - a;
                    guide = t;
                }
            }
        }
        if (best > SNAP) return new int[]{ topLeft, Integer.MIN_VALUE };
        return new int[]{ snapped, guide };
    }

    private int[] xTargets(List<HudModule> els, HudModule skip) {
        List<Integer> t = new ArrayList<>();
        t.add(0);
        t.add(this.width / 2);
        t.add(this.width);
        for (HudModule el : els) {
            if (el == skip) continue;
            int x0 = screenX(el), w = el.boxW();
            t.add(x0);
            t.add(x0 + w / 2);
            t.add(x0 + w);
        }
        return toArray(t);
    }

    private int[] yTargets(List<HudModule> els, HudModule skip) {
        List<Integer> t = new ArrayList<>();
        t.add(0);
        t.add(this.height / 2);
        t.add(this.height);
        for (HudModule el : els) {
            if (el == skip) continue;
            int y0 = screenY(el), h = el.boxH();
            t.add(y0);
            t.add(y0 + h / 2);
            t.add(y0 + h);
        }
        return toArray(t);
    }

    private static int[] toArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private void outline(GuiGraphics ctx, int x1, int y1, int x2, int y2, int color) {
        ctx.fill(x1, y1, x2, y1 + 1, color);
        ctx.fill(x1, y2 - 1, x2, y2, color);
        ctx.fill(x1, y1, x1 + 1, y2, color);
        ctx.fill(x2 - 1, y1, x2, y2, color);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        int mx = (int) click.x();
        int my = (int) click.y();
        HudModule hit = topAt(elements(), mx, my);
        if (hit != null) {
            if (click.button() == 1) {
                hit.resetOffset();
                return true;
            }
            dragging = hit;
            grabDX = mx - screenX(hit);
            grabDY = my - screenY(hit);
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (dragging != null) {
            dragging = null;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
