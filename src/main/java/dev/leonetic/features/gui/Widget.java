package dev.leonetic.features.gui;

import dev.leonetic.features.Feature;
import dev.leonetic.features.gui.items.Item;
import dev.leonetic.features.gui.items.buttons.Button;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.modules.client.ClickGuiModule;
import dev.leonetic.util.render.GuiFade;
import dev.leonetic.util.render.RenderUtil;
import dev.leonetic.util.render.ScissorUtil;
import dev.leonetic.util.render.font.Fonts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class Widget extends Feature {

    public static Color currentAccent = null;

    public static Module.Category currentCategory = null;

    public static final int PANEL_WIDTH   = GuiTheme.PANEL_WIDTH;
    public static final int HEADER_HEIGHT = GuiTheme.HEADER_HEIGHT;

    private final Module.Category category;
    protected GuiGraphics context;
    private final List<Item> items = new ArrayList<>();
    public boolean drag;
    private int x;
    private int y;
    private int x2;
    private int y2;
    private int width;
    private int height;
    private boolean open;
    private boolean hidden = false;
    private float animatedHeight = 0f;
    private float scrollOffset = 0f;

    public Widget(String name, Module.Category category, int x, int y, boolean open) {
        super(name);
        this.category = category;
        this.x = x;
        this.y = y;
        this.width = GuiTheme.PANEL_WIDTH;
        this.height = GuiTheme.HEADER_HEIGHT;
        this.open = open;
    }

    public Widget(String name, int x, int y, boolean open) {
        this(name, null, x, y, open);
    }

    public Color accent() {
        return GuiTheme.categoryColor(category);
    }

    public Color accentAt(float yOffset) {
        return GuiTheme.categoryColor(category, yOffset);
    }

    public Module.Category getCategory() {
        return this.category;
    }

    private void drag(int mouseX, int mouseY) {
        if (!this.drag) return;
        this.x = this.x2 + mouseX;
        this.y = this.y2 + mouseY;
    }

    public void drawScreen(GuiGraphics context, int mouseX, int mouseY, float partialTicks) {
        this.context = context;
        Item.context = context;
        this.drag(mouseX, mouseY);

        int headerTop    = this.y;
        int headerBottom = this.y + this.height;
        int bodyTop      = headerBottom;

        int screenHeight = (int) (mc.getWindow().getGuiScaledHeight() / HomovoreGui.getScale());
        int availableHeight = Math.max(0, screenHeight - bodyTop - 6);
        float contentHeight = this.getTotalItemHeight();
        float targetHeight  = this.open ? Math.min(contentHeight, availableHeight) : 0.0f;
        animatedHeight += (targetHeight - animatedHeight) * ClickGuiModule.getInstance().getExpandSpeed();
        if (Math.abs(animatedHeight - targetHeight) < 0.5f) animatedHeight = targetHeight;

        Color cat = accentAt(headerTop);
        int catRGB = cat.getRGB();

        RenderUtil.rect(context, this.x, headerTop, this.x + this.width, headerBottom, catRGB);

        RenderUtil.rect(context, this.x, headerTop, this.x + this.width, headerTop + 1f, GuiTheme.HIGHLIGHT_TOP);

        String title = truncateText(this.getName(), this.width - 14);
        int textY = headerTop + (GuiTheme.HEADER_HEIGHT - 8) / 2 + 1;
        drawString(title, (float) this.x + 4.0f, textY, GuiTheme.TEXT_HEADER);
        String caret = this.open ? "-" : "+";
        drawString(caret, (float) (this.x + this.width - 8), textY, GuiTheme.TEXT_HEADER);

        if (animatedHeight > 0.5f) {
            int scissorBottom = (int) (bodyTop + animatedHeight);
            float maxScroll = Math.max(0, contentHeight - animatedHeight);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

            scrollSelectionIntoView(animatedHeight, maxScroll);

            RenderUtil.rect(context, this.x, bodyTop, this.x + this.width, scissorBottom, GuiTheme.BODY_BG);

            RenderUtil.rect(context, this.x, bodyTop - 1f, this.x + this.width, bodyTop, GuiTheme.OUTLINE);

            ScissorUtil.enable(context, x, bodyTop, x + width, scissorBottom);

            currentCategory = category;
            float yCursor = (float) bodyTop - scrollOffset;
            for (Item item : this.getItems()) {
                if (item.isHidden()) continue;
                item.setLocation((float) this.x, yCursor);
                item.setWidth(this.width);
                currentAccent = GuiTheme.moduleColor(category, yCursor);
                if (item.isHovering(mouseX, mouseY)) ScissorUtil.disable(context);
                item.drawScreen(context, mouseX, mouseY, partialTicks);
                if (item.isHovering(mouseX, mouseY)) ScissorUtil.enable(context);

                if (GuiNavigator.isSelected(item) && !(item instanceof dev.leonetic.features.gui.items.buttons.ModuleButton)) {
                    Button.drawSelectionRing(context, this.x, item.getY(),
                            this.x + this.width, item.getY() + item.getRowHeight() - 1f, currentAccent);
                }
                yCursor += item.getHeight();
            }
            currentAccent = null;
            currentCategory = null;

            ScissorUtil.disable(context);

            RenderUtil.rect(context, this.x, headerTop, this.x + this.width, scissorBottom, GuiTheme.OUTLINE, 1f);

            boolean anyAnimating = false;
            for (Item item : this.getItems()) {
                if (item instanceof dev.leonetic.features.gui.items.buttons.ModuleButton mb && mb.isAnimating()) {
                    anyAnimating = true;
                    break;
                }
            }
            if (maxScroll > 0.5f && !anyAnimating) {
                float trackTop = bodyTop + 2f;
                float trackBottom = scissorBottom - 2f;
                float trackH = trackBottom - trackTop;
                float thumbH = Math.max(8f, trackH * (animatedHeight / contentHeight));
                float thumbY = trackTop + (trackH - thumbH) * (scrollOffset / maxScroll);
                int thumbColor = GuiTheme.withAlpha(cat, 220).getRGB();
                RenderUtil.rect(context, this.x + this.width - 2f, thumbY,
                        this.x + this.width - 1f, thumbY + thumbH, thumbColor);
            }
        } else {

            RenderUtil.rect(context, this.x, headerTop, this.x + this.width, headerBottom, GuiTheme.OUTLINE, 1f);
        }
    }

    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && this.isHovering(mouseX, mouseY)) {
            this.x2 = this.x - mouseX;
            this.y2 = this.y - mouseY;
            HomovoreGui.getClickGui().getComponents().forEach(c -> { if (c.drag) c.drag = false; });
            this.drag = true;
            return;
        }
        if (mouseButton == 1 && this.isHovering(mouseX, mouseY)) {
            this.open = !this.open;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
            return;
        }
        if (!this.open) return;
        int bodyTop = this.y + this.height;
        int scissorBottom = (int) (bodyTop + animatedHeight);
        this.getItems().forEach(item -> {
            if (item.getY() + item.getHeight() > bodyTop && item.getY() < scissorBottom) {
                item.mouseClicked(mouseX, mouseY, mouseButton);
            }
        });
    }

    public void mouseReleased(int mouseX, int mouseY, int releaseButton) {
        if (releaseButton == 0) this.drag = false;
        if (!this.open) return;
        int bodyTop = this.y + this.height;
        int scissorBottom = (int) (bodyTop + animatedHeight);
        this.getItems().forEach(item -> {
            if (item.getY() + item.getHeight() > bodyTop && item.getY() < scissorBottom) {
                item.mouseReleased(mouseX, mouseY, releaseButton);
            }
        });
    }

    public void onKeyTyped(String typedChar, int keyCode) {
        if (!this.open) return;
        this.getItems().forEach(item -> item.onKeyTyped(typedChar, keyCode));
    }

    public void onKeyPressed(int key) {
        if (!open) return;
        this.getItems().forEach(item -> item.onKeyPressed(key));
    }

    public void addButton(Button button) { this.items.add(button); }

    public int getX() { return this.x; }
    public void setX(int x) { this.x = x; }
    public int getY() { return this.y; }
    public void setY(int y) { this.y = y; }
    public int getWidth() { return this.width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return this.height; }
    public void setHeight(int height) { this.height = height; }
    public boolean isHidden() { return this.hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
    public boolean isOpen() { return this.open; }
    public final List<Item> getItems() { return this.items; }

    public boolean isHoveringBody(int mouseX, int mouseY) {
        if (!open || animatedHeight <= 0) return false;
        int bodyTop = this.y + this.height;
        int scissorBottom = (int) (bodyTop + animatedHeight);
        return mouseX >= x && mouseX <= x + width && mouseY >= bodyTop && mouseY <= scissorBottom;
    }

    public void scroll(double amount) {
        float maxScroll = Math.max(0, getTotalItemHeight() - animatedHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset - (float) amount * 12, maxScroll));
    }

    private void scrollSelectionIntoView(float visibleHeight, float maxScroll) {
        if (!GuiNavigator.isFocusedPanel(this)) return;
        Item sel = GuiNavigator.getSelected();
        if (sel == null) return;

        Float selTop = null;
        int selHeight = 0;
        float contentTop = 0f;
        for (Item item : this.getItems()) {
            if (item.isHidden()) continue;
            if (item == sel) { selTop = contentTop; selHeight = item.getRowHeight(); }
            if (item instanceof dev.leonetic.features.gui.items.buttons.ModuleButton mb && mb.isSubOpen()) {
                float settingTop = contentTop + GuiTheme.MODULE_HEIGHT
                        + (mb.hasMultiplePages() ? dev.leonetic.features.gui.items.buttons.ModuleButton.SWITCHER_HEIGHT : 0);
                for (Item setting : mb.currentPageVisibleSettings()) {
                    if (setting == sel) { selTop = settingTop; selHeight = setting.getRowHeight(); }
                    settingTop += setting.getHeight();
                }
            }
            contentTop += item.getHeight();
        }
        if (selTop == null) return;

        if (selTop < scrollOffset) {
            scrollOffset = selTop;
        } else if (selTop + selHeight > scrollOffset + visibleHeight) {
            scrollOffset = selTop + selHeight - visibleHeight;
        }
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    public boolean isHovering(int mouseX, int mouseY) {
        return mouseX >= this.x && mouseX <= this.x + this.width
                && mouseY >= this.y && mouseY <= this.y + this.height;
    }

    private float getTotalItemHeight() {
        float h = 0f;
        for (Item item : this.getItems()) {
            if (item.isHidden()) continue;
            h += item.getHeight();
        }
        return h;
    }

    private String truncateText(String text, int maxWidth) {
        if (Fonts.width(text) <= maxWidth) return text;
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

    protected void drawString(String text, double x, double y, int color) {
        Fonts.drawString(context, text, (float) x, (float) y, GuiFade.apply(color));
    }
}
