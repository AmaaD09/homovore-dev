package dev.leonetic.features.gui.items.buttons;

import dev.leonetic.features.gui.GuiTheme;
import dev.leonetic.features.gui.HomovoreGui;
import dev.leonetic.features.gui.Widget;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.render.RenderUtil;
import dev.leonetic.util.render.font.Fonts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.awt.Color;

public class ColorButton extends SettingButton<Color> {
    private static final int SV_HEIGHT = 44;
    private static final int BAR_HEIGHT = 6;
    private static final int BTN_HEIGHT = 10;
    private static final int GAP = 2;

    private boolean open = false;
    private boolean hoveringHue, hoveringColor, hoveringAlpha, hoveringCopy, hoveringPaste;
    private boolean draggingHue, draggingColor, draggingAlpha;
    private float[] hsb;

    public ColorButton(Setting<Color> setting) {
        super(setting);
        this.height = GuiTheme.SETTING_HEIGHT;
        hsb = Color.RGBtoHSB(setting.getValue().getRed(), setting.getValue().getGreen(), setting.getValue().getBlue(), null);
    }

    @Override
    public int getHeight() {
        if (!open) return GuiTheme.SETTING_HEIGHT;
        return GuiTheme.SETTING_HEIGHT + GAP + SV_HEIGHT + GAP + BAR_HEIGHT + GAP + BAR_HEIGHT + GAP + BTN_HEIGHT + GAP;
    }

    @Override
    public int getRowHeight() { return GuiTheme.SETTING_HEIGHT; }

    @Override
    public void keyActivate() {

        open = !open;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    private int pickerWidth() {
        return Math.max(40, this.width - 4);
    }

    @Override
    public void drawScreen(GuiGraphics context, int mouseX, int mouseY, float partialTicks) {
        boolean hovering = isHoveringRow(mouseX, mouseY);
        if (hovering) {
            RenderUtil.rect(context, this.x, this.y, this.x + this.width, this.y + GuiTheme.SETTING_HEIGHT,
                    GuiTheme.SLIDER_TRACK);
        }
        drawScrollableString(this.getName(), this.x + 2f,
                this.y + (GuiTheme.SETTING_HEIGHT - 8) / 2f,
                GuiTheme.TEXT_SETTING, this.width - 22, hovering);

        float sw1 = this.x + this.width - 16f;
        float sw2 = this.x + this.width - 3f;
        float sy1 = this.y + 2f;
        float sy2 = this.y + GuiTheme.SETTING_HEIGHT - 2f;
        RenderUtil.rect(context, sw1, sy1, sw2, sy2, setting.getValue().getRGB());
        RenderUtil.rect(context, sw1, sy1, sw2, sy2, GuiTheme.OUTLINE, 1f);

        if (!open) return;

        Color currentColor = setting.getValue();
        Color realColor = Color.getHSBColor(hsb[0], 1, 1);
        int outlineColor = GuiTheme.OUTLINE;

        int pickerWidth = pickerWidth();
        float pickerX = this.x + 2f;
        float yOffset = GuiTheme.SETTING_HEIGHT + GAP;

        int dragX = Mth.clamp(mouseX - (int) pickerX, 0, pickerWidth);
        int dragY = Mth.clamp(mouseY - (int) (this.y + yOffset), 0, SV_HEIGHT);
        float dragHue = Math.max(pickerWidth * hsb[0] - .5f, 1);
        float dragSaturation = Math.max(pickerWidth * hsb[1] - 1, 2);
        float dragBrightness = Math.max(SV_HEIGHT * (1.0f - hsb[2]) - 1, 2);
        float dragAlpha = Math.max(pickerWidth * (currentColor.getAlpha() / 255.0f) - .5f, 1);

        RenderUtil.horizontalGradient(context, pickerX, this.y + yOffset, pickerX + pickerWidth, this.y + yOffset + SV_HEIGHT, Color.WHITE, realColor);
        RenderUtil.verticalGradient(context, pickerX, this.y + yOffset, pickerX + pickerWidth, this.y + yOffset + SV_HEIGHT, new Color(0, 0, 0, 0), Color.BLACK);
        RenderUtil.rect(context, pickerX, this.y + yOffset, pickerX + pickerWidth, this.y + yOffset + SV_HEIGHT, outlineColor, 1.0f);
        hoveringColor = isHoveringArea(mouseX, mouseY, pickerX, this.y + yOffset, pickerX + pickerWidth, this.y + yOffset + SV_HEIGHT);
        if (dragSaturation < pickerWidth && dragBrightness < SV_HEIGHT) {
            RenderUtil.rect(context, pickerX + dragSaturation - 2f, this.y + yOffset + dragBrightness - 2f,
                    pickerX + dragSaturation + 1f, this.y + yOffset + dragBrightness + 1f, 0xFF000000);
            RenderUtil.rect(context, pickerX + dragSaturation - 1f, this.y + yOffset + dragBrightness - 1f,
                    pickerX + dragSaturation, this.y + yOffset + dragBrightness, 0xFFFFFFFF);
        }
        if (draggingColor) {
            hsb[1] = (float) dragX / pickerWidth;
            hsb[2] = 1.0f - (float) dragY / SV_HEIGHT;
            setColor(hsb);
        }

        yOffset += SV_HEIGHT + GAP;

        RenderUtil.horizontalGradient(context, pickerX, this.y + yOffset, pickerX + pickerWidth, this.y + yOffset + BAR_HEIGHT,
                new Color(currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue(), 0),
                new Color(currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue(), 255));
        RenderUtil.rect(context, pickerX, this.y + yOffset, pickerX + pickerWidth, this.y + yOffset + BAR_HEIGHT, outlineColor, 1.0f);
        hoveringAlpha = isHoveringArea(mouseX, mouseY, pickerX, this.y + yOffset, pickerX + pickerWidth, this.y + yOffset + BAR_HEIGHT);
        RenderUtil.rect(context, pickerX + dragAlpha - 1.5f, this.y + yOffset - 1,
                pickerX + dragAlpha + 1.5f, this.y + yOffset + BAR_HEIGHT + 1, 0xFF000000);
        RenderUtil.rect(context, pickerX + dragAlpha - 0.5f, this.y + yOffset,
                pickerX + dragAlpha + 0.5f, this.y + yOffset + BAR_HEIGHT, 0xFFFFFFFF);
        if (draggingAlpha) {
            setColor(hsb, (int) (255 * (float) dragX / pickerWidth));
        }

        yOffset += BAR_HEIGHT + GAP;

        for (float i = 0; i < pickerWidth; i += 0.5f) {
            RenderUtil.rect(context, pickerX + i, this.y + yOffset, pickerX + i + 0.5f, this.y + yOffset + BAR_HEIGHT,
                    Color.getHSBColor(i / pickerWidth, 1.0f, 1.0f).getRGB());
        }
        RenderUtil.rect(context, pickerX, this.y + yOffset, pickerX + pickerWidth, this.y + yOffset + BAR_HEIGHT, outlineColor, 1.0f);
        hoveringHue = isHoveringArea(mouseX, mouseY, pickerX, this.y + yOffset, pickerX + pickerWidth, this.y + yOffset + BAR_HEIGHT);
        if (dragHue < pickerWidth) {
            RenderUtil.rect(context, pickerX + dragHue - 1.5f, this.y + yOffset - 1,
                    pickerX + dragHue + 1.5f, this.y + yOffset + BAR_HEIGHT + 1, 0xFF000000);
            RenderUtil.rect(context, pickerX + dragHue - 0.5f, this.y + yOffset,
                    pickerX + dragHue + 0.5f, this.y + yOffset + BAR_HEIGHT, 0xFFFFFFFF);
        }
        if (draggingHue) {
            hsb[0] = (float) dragX / pickerWidth;
            setColor(hsb);
        }

        yOffset += BAR_HEIGHT + GAP;

        int buttonWidth = (pickerWidth - 2) / 2;
        Color accent = Widget.currentAccent != null ? Widget.currentAccent : new Color(120, 175, 220);
        int activeBg = accent.getRGB();
        int idleBg = GuiTheme.SLIDER_TRACK;
        float textY = this.y + yOffset + (BTN_HEIGHT - 8) / 2f + 1;

        RenderUtil.rect(context, pickerX, this.y + yOffset, pickerX + buttonWidth, this.y + yOffset + BTN_HEIGHT,
                hoveringCopy ? activeBg : idleBg);
        RenderUtil.rect(context, pickerX, this.y + yOffset, pickerX + buttonWidth, this.y + yOffset + BTN_HEIGHT, outlineColor, 1f);
        drawString("Copy", pickerX + buttonWidth / 2.0 - Fonts.width("Copy") / 2.0, textY, GuiTheme.TEXT_SETTING);
        hoveringCopy = isHoveringArea(mouseX, mouseY, pickerX, this.y + yOffset, pickerX + buttonWidth, this.y + yOffset + BTN_HEIGHT);

        RenderUtil.rect(context, pickerX + buttonWidth + 2, this.y + yOffset, pickerX + buttonWidth * 2 + 2, this.y + yOffset + BTN_HEIGHT,
                hoveringPaste ? activeBg : idleBg);
        RenderUtil.rect(context, pickerX + buttonWidth + 2, this.y + yOffset, pickerX + buttonWidth * 2 + 2, this.y + yOffset + BTN_HEIGHT, outlineColor, 1f);
        drawString("Paste", pickerX + buttonWidth + 2 + buttonWidth / 2.0 - Fonts.width("Paste") / 2.0, textY, GuiTheme.TEXT_SETTING);
        hoveringPaste = isHoveringArea(mouseX, mouseY, pickerX + buttonWidth + 2, this.y + yOffset, pickerX + buttonWidth * 2 + 2, this.y + yOffset + BTN_HEIGHT);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (isHoveringRow(mouseX, mouseY) && mouseButton == 1) {
            open = !open;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
        }
        if (mouseButton == 0) {
            if (hoveringHue) draggingHue = true;
            if (hoveringColor) draggingColor = true;
            if (hoveringAlpha) draggingAlpha = true;
            if (hoveringCopy) {
                HomovoreGui.setColorClipboard(setting.getValue());
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
            }
            if (hoveringPaste && HomovoreGui.getColorClipboard() != null) {
                setting.setValue(HomovoreGui.getColorClipboard());
                hsb = Color.RGBtoHSB(setting.getValue().getRed(), setting.getValue().getGreen(), setting.getValue().getBlue(), null);
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
            }
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int releaseButton) {
        if (releaseButton == 0) {
            draggingHue = false;
            draggingColor = false;
            draggingAlpha = false;
        }
    }

    @Override
    public boolean isHovering(int mouseX, int mouseY) {
        return isHoveringRow(mouseX, mouseY);
    }

    private boolean isHoveringRow(int mouseX, int mouseY) {
        for (Widget widget : HomovoreGui.getClickGui().getComponents()) {
            if (widget.drag) return false;
        }
        return mouseX >= this.x && mouseX <= this.x + this.width
                && mouseY >= this.y && mouseY < this.y + GuiTheme.SETTING_HEIGHT;
    }

    private boolean isHoveringArea(int mouseX, int mouseY, float left, float top, float right, float bottom) {
        for (Widget widget : HomovoreGui.getClickGui().getComponents()) {
            if (widget.drag) return false;
        }
        return left <= mouseX && top <= mouseY && right > mouseX && bottom > mouseY;
    }

    private void setColor(float[] hsb) { setColor(hsb, setting.getValue().getAlpha()); }

    private void setColor(float[] hsb, int alpha) {
        Color color = new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]));
        setting.setValue(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
    }
}
