package dev.leonetic.features.gui.items.buttons;

import dev.leonetic.features.gui.GuiTheme;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.render.RenderUtil;
import dev.leonetic.util.render.font.Fonts;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

public class EnumButton extends SettingButton<Enum<?>> {
    public EnumButton(Setting<Enum<?>> setting) {
        super(setting);
        this.height = GuiTheme.SETTING_HEIGHT;
    }

    @Override
    public int getHeight() { return GuiTheme.SETTING_HEIGHT; }

    @Override
    public void drawScreen(GuiGraphics context, int mouseX, int mouseY, float partialTicks) {
        boolean hovering = this.isHovering(mouseX, mouseY);
        if (hovering) {
            RenderUtil.rect(context, this.x, this.y, this.x + this.width, this.y + this.height,
                    GuiTheme.SLIDER_TRACK);
        }
        float ty = this.y + (this.height - 8) / 2f;
        String val = ChatFormatting.GRAY + this.setting.currentEnumName();
        int w = Fonts.width(val);
        drawString(val, this.x + this.width - w - 3f, ty, GuiTheme.TEXT_SETTING_VALUE);
        int labelMax = Math.max(0, this.width - w - 8);
        drawScrollableString(this.setting.getName(), this.x + 2f, ty, GuiTheme.TEXT_SETTING, labelMax, hovering);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.isHovering(mouseX, mouseY)) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
        }
    }

    @Override
    public void toggle() { this.setting.increaseEnum(); }

    @Override
    public boolean getState() { return true; }
}
