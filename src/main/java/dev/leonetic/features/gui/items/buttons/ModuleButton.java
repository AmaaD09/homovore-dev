package dev.leonetic.features.gui.items.buttons;

import dev.leonetic.features.gui.GuiNavigator;
import dev.leonetic.features.gui.GuiTheme;
import dev.leonetic.features.gui.Widget;
import dev.leonetic.features.gui.items.Item;
import dev.leonetic.features.gui.items.SearchBar;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.modules.client.ClickGuiModule;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.render.RenderUtil;
import dev.leonetic.util.render.font.Fonts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

public class ModuleButton extends Button {
    public static final int SWITCHER_HEIGHT = 10;
    private static final int ARROW_HITBOX = 10;
    private static final String DEFAULT_PAGE = "General";

    private final Module module;
    private final List<Item> items = new ArrayList<>();
    private String currentPageName = DEFAULT_PAGE;
    private boolean subOpen;
    private float animatedSubHeight = 0f;
    private boolean hoveringPagePrev, hoveringPageNext;

    public ModuleButton(Module module) {
        super(module.getName());
        this.module = module;
        buildItems();
    }

    private void buildItems() {
        items.clear();

        List<Setting<?>> tail = new ArrayList<>();
        for (Setting<?> setting : module.getSettings()) {
            if (setting == module.enabled || setting == module.displayName) continue;
            if (setting == module.bind || setting == module.bindMode) {
                tail.add(setting);
                continue;
            }
            SettingButton<?> btn = SettingButton.create(setting);
            if (btn != null) items.add(btn);
        }
        for (Setting<?> setting : tail) {
            SettingButton<?> btn = SettingButton.create(setting);
            if (btn != null) items.add(btn);
        }
    }

    private List<String> visiblePages() {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Item item : items) {
            if (item.isHidden()) continue;
            seen.add(item.getPage());
        }

        boolean hadDefault = seen.remove(DEFAULT_PAGE);
        List<String> out = new ArrayList<>(seen);
        if (hadDefault) out.add(DEFAULT_PAGE);
        if (out.isEmpty()) out.add(DEFAULT_PAGE);
        return out;
    }

    private String resolveCurrentPage(List<String> pages) {
        for (String p : pages) {
            if (p.equalsIgnoreCase(currentPageName)) return p;
        }
        return pages.get(0);
    }

    private boolean onPage(Item item, String page) {
        return item.getPage().equalsIgnoreCase(page);
    }

    private void forEachActive(String page, Consumer<Item> consumer) {
        for (Item item : items) {
            if (item.isHidden()) continue;
            if (!onPage(item, page)) continue;
            consumer.accept(item);
        }
    }

    private float getTotalSubHeight(List<String> pages, String page) {
        float h = 0;
        if (pages.size() > 1) h += SWITCHER_HEIGHT;
        for (Item item : items) {
            if (item.isHidden()) continue;
            if (!onPage(item, page)) continue;
            h += item.getHeight();
        }
        return h;
    }

    @Override
    public void drawScreen(GuiGraphics context, int mouseX, int mouseY, float partialTicks) {

        for (Item item : items) item.update();

        List<String> pages = visiblePages();
        String page = resolveCurrentPage(pages);
        currentPageName = page;

        boolean hovering = this.isHovering(mouseX, mouseY);
        boolean searching = SearchBar.hasQuery();
        boolean match = searching && SearchBar.matches(this.getName());
        boolean navSelected = GuiNavigator.isSelected(this);

        drawRow(context, this.x, this.y, this.x + this.width, this.y + GuiTheme.MODULE_HEIGHT,
                this.getState(), hovering);

        if (match || navSelected) {
            java.awt.Color accent = Widget.currentAccent != null
                    ? Widget.currentAccent : new java.awt.Color(110, 120, 130);
            float rx2 = this.x + this.width;
            float ry2 = this.y + GuiTheme.MODULE_HEIGHT - 1f;

            if (!this.getState()) {
                RenderUtil.rect(context, this.x, this.y, rx2, ry2,
                        GuiTheme.withAlpha(accent, 70).getRGB());
            }
            drawSelectionRing(context, this.x, this.y, rx2, ry2, accent);
        }

        int textColor;
        if (searching && !match) {
            textColor = 0x55FFFFFF;
        } else if (match) {
            textColor = 0xFFFFFFFF;
        } else {
            textColor = this.getState() ? GuiTheme.TEXT_MODULE_ON : GuiTheme.TEXT_MODULE_OFF;
        }
        drawScrollableString(this.getName(), this.x + 4f,
                this.y + (GuiTheme.MODULE_HEIGHT - 8) / 2f,
                textColor, this.width - 16, hovering);

        if (!items.isEmpty()) {
            String arrow = subOpen ? "-" : "+";
            drawString(arrow,
                    this.x + this.width - 8,
                    this.y + (GuiTheme.MODULE_HEIGHT - 8) / 2f,
                    textColor);
        }

        float targetSubHeight = subOpen ? getTotalSubHeight(pages, page) : 0f;
        animatedSubHeight += (targetSubHeight - animatedSubHeight) * ClickGuiModule.getInstance().getExpandSpeed();
        if (Math.abs(animatedSubHeight - targetSubHeight) < 0.5f) animatedSubHeight = targetSubHeight;

        if (animatedSubHeight > 0.5f) {
            float trayTop = this.y + GuiTheme.MODULE_HEIGHT;
            float trayBottom = trayTop + animatedSubHeight;
            boolean animating = Math.abs(animatedSubHeight - targetSubHeight) >= 0.5f;
            if (animating) {
                context.enableScissor((int) this.x, (int) trayTop,
                        (int) (this.x + this.width), (int) trayBottom);
            }
            RenderUtil.rect(context, this.x, trayTop, this.x + this.width, trayBottom,
                    GuiTheme.SETTINGS_TRAY_BG);

            java.awt.Color accent = Widget.currentAccent != null
                    ? Widget.currentAccent : new java.awt.Color(110, 120, 130);
            int stripe = GuiTheme.withAlpha(accent, 200).getRGB();
            RenderUtil.rect(context, this.x, trayTop, this.x + 2f, trayBottom, stripe);

            float settingX = this.x + GuiTheme.SETTING_INDENT;
            int settingWidth = this.width - GuiTheme.SETTING_INDENT - GuiTheme.SETTING_RIGHT_PAD;
            float h = 0f;

            if (pages.size() > 1) {
                drawPageSwitcher(context, trayTop + h, mouseX, mouseY, page);
                h += SWITCHER_HEIGHT;
            } else {
                hoveringPagePrev = false;
                hoveringPageNext = false;
            }

            java.awt.Color savedAccent = Widget.currentAccent;
            for (Item item : items) {
                if (item.isHidden()) continue;
                if (!onPage(item, page)) continue;
                item.setLocation(settingX, trayTop + h);
                item.setWidth(settingWidth);
                Widget.currentAccent = GuiTheme.moduleColor(Widget.currentCategory, trayTop + h);
                item.drawScreen(context, mouseX, mouseY, partialTicks);
                if (GuiNavigator.isSelected(item)) {
                    drawSelectionRing(context, item.getX(), item.getY(),
                            item.getX() + settingWidth, item.getY() + item.getRowHeight() - 1f,
                            Widget.currentAccent);
                }
                h += item.getHeight();
            }
            Widget.currentAccent = savedAccent;

            if (animating) {
                context.disableScissor();
            }
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (items.isEmpty()) return;
        if (mouseButton == 1 && this.isHovering(mouseX, mouseY)) {
            this.subOpen = !this.subOpen;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
        }
        if (!subOpen) return;
        List<String> pages = visiblePages();
        String page = resolveCurrentPage(pages);
        if (mouseButton == 0 && pages.size() > 1) {
            int idx = pages.indexOf(page);
            if (hoveringPagePrev) {
                currentPageName = pages.get((idx - 1 + pages.size()) % pages.size());
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                return;
            }
            if (hoveringPageNext) {
                currentPageName = pages.get((idx + 1) % pages.size());
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                return;
            }
        }
        forEachActive(page, item -> item.mouseClicked(mouseX, mouseY, mouseButton));
    }

    private void drawPageSwitcher(GuiGraphics context, float top, int mouseX, int mouseY, String page) {

        float left = this.x + GuiTheme.SETTING_INDENT;
        float right = this.x + this.width - GuiTheme.SETTING_RIGHT_PAD;
        float bottom = top + SWITCHER_HEIGHT;

        hoveringPagePrev = mouseX >= left && mouseX < left + ARROW_HITBOX && mouseY >= top && mouseY < bottom;
        hoveringPageNext = mouseX >= right - ARROW_HITBOX && mouseX < right && mouseY >= top && mouseY < bottom;

        float textY = top + (SWITCHER_HEIGHT - 8) / 2f;
        int prevColor = hoveringPagePrev ? GuiTheme.TEXT_SETTING : GuiTheme.TEXT_SETTING_VALUE;
        int nextColor = hoveringPageNext ? GuiTheme.TEXT_SETTING : GuiTheme.TEXT_SETTING_VALUE;
        drawString("<", left + 2f, textY, prevColor);
        drawString(">", right - 6f, textY, nextColor);

        int tw = Fonts.width(page);
        drawString(page, this.x + (this.width - tw) / 2f, textY, GuiTheme.TEXT_SETTING_VALUE);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int releaseButton) {
        super.mouseReleased(mouseX, mouseY, releaseButton);
        if (!subOpen || items.isEmpty()) return;
        String page = resolveCurrentPage(visiblePages());
        forEachActive(page, item -> item.mouseReleased(mouseX, mouseY, releaseButton));
    }

    @Override
    public void onKeyTyped(String typedChar, int keyCode) {
        super.onKeyTyped(typedChar, keyCode);
        if (!subOpen || items.isEmpty()) return;
        String page = resolveCurrentPage(visiblePages());
        forEachActive(page, item -> item.onKeyTyped(typedChar, keyCode));
    }

    @Override
    public void onKeyPressed(int key) {
        super.onKeyPressed(key);
        if (!subOpen || items.isEmpty()) return;
        String page = resolveCurrentPage(visiblePages());
        forEachActive(page, item -> item.onKeyPressed(key));
    }

    @Override
    public int getHeight() {
        if (animatedSubHeight <= 0) return GuiTheme.MODULE_HEIGHT;
        return GuiTheme.MODULE_HEIGHT + (int) Math.ceil(animatedSubHeight);
    }

    public boolean isAnimating() {
        float target = subOpen ? getTotalSubHeight(visiblePages(), resolveCurrentPage(visiblePages())) : 0f;
        return Math.abs(animatedSubHeight - target) >= 0.5f;
    }

    public Module getModule() {
        return this.module;
    }

    @Override
    public int getRowHeight() { return GuiTheme.MODULE_HEIGHT; }

    public boolean isSubOpen() { return this.subOpen; }

    public void toggleSubOpen() {
        if (items.isEmpty()) return;
        this.subOpen = !this.subOpen;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    public boolean hasMultiplePages() { return visiblePages().size() > 1; }

    public void cyclePage(int dir) {
        List<String> pages = visiblePages();
        if (pages.size() <= 1) return;
        int idx = pages.indexOf(resolveCurrentPage(pages));
        currentPageName = pages.get(((idx + dir) % pages.size() + pages.size()) % pages.size());
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    public List<Item> currentPageVisibleSettings() {
        List<Item> out = new ArrayList<>();
        if (!subOpen) return out;
        String page = resolveCurrentPage(visiblePages());
        for (Item item : items) {
            if (item.isHidden()) continue;
            if (!onPage(item, page)) continue;
            out.add(item);
        }
        return out;
    }

    @Override
    public void toggle() {
        this.module.toggle();
    }

    @Override
    public boolean getState() {
        return this.module.isEnabled();
    }
}
