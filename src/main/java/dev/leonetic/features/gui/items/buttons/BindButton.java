package dev.leonetic.features.gui.items.buttons;

import dev.leonetic.features.gui.GuiTheme;
import dev.leonetic.features.settings.Bind;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.render.RenderUtil;
import dev.leonetic.util.render.font.Fonts;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

public class BindButton extends SettingButton<Bind> {
    public boolean isListening;

    public BindButton(Setting<Bind> setting) {
        super(setting);
        this.height = GuiTheme.SETTING_HEIGHT;
    }

    @Override
    public int getHeight() { return GuiTheme.SETTING_HEIGHT; }

    @Override
    public void drawScreen(GuiGraphics context, int mouseX, int mouseY, float partialTicks) {
        boolean hovering = this.isHovering(mouseX, mouseY);
        if (hovering || isListening) {
            RenderUtil.rect(context, this.x, this.y, this.x + this.width, this.y + this.height,
                    GuiTheme.SLIDER_TRACK);
        }
        float ty = this.y + (this.height - 8) / 2f;
        if (isListening) {
            drawString("Press a key...", this.x + 2f, ty, GuiTheme.TEXT_SETTING);
        } else {
            String str = ChatFormatting.GRAY + this.setting.getValue().toString().toUpperCase()
                    .replace("KEY.KEYBOARD", "").replace(".", " ").trim();
            int w = Fonts.width(str);
            drawString(str, this.x + this.width - w - 3f, ty, GuiTheme.TEXT_SETTING_VALUE);
            int labelMax = Math.max(0, this.width - w - 8);
            drawScrollableString(this.setting.getName(), this.x + 2f, ty, GuiTheme.TEXT_SETTING, labelMax, hovering);
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (this.isListening) {
            if (mouseButton == 0 && this.isHovering(mouseX, mouseY)) {
                this.onMouseClick();
                return;
            }
            Bind bind = Bind.fromMouseButton(mouseButton);
            this.setting.setValue(bind);
            this.isListening = false;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.isHovering(mouseX, mouseY)) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
        }
    }

    @Override
    public void onKeyPressed(int key) {
        if (this.isListening) {
            Bind bind = new Bind(key);
            if (key == GLFW.GLFW_KEY_DELETE
                    || key == GLFW.GLFW_KEY_BACKSPACE
                    || key == GLFW.GLFW_KEY_ESCAPE) {
                bind = new Bind(-1);
            }
            this.setting.setValue(bind);
            this.onMouseClick();
        }
    }

    @Override
    public void toggle() { this.isListening = !this.isListening; }

    @Override
    public boolean getState() { return !this.isListening; }
}
