package dev.leonetic.features.gui.items.buttons;

import dev.leonetic.features.gui.GuiTheme;
import dev.leonetic.features.gui.Widget;
import dev.leonetic.features.gui.items.TextBox;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.render.font.Fonts;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;

public class StringButton extends SettingButton<String> {
    private final TextBox textBox;

    public StringButton(Setting<String> setting) {
        super(setting);
        this.height = GuiTheme.SETTING_HEIGHT;
        this.textBox = new TextBox()
                .placeholder(setting.getName())
                .onCommit(this::commit)
                .onCancel(() -> textBox().setText(setting.getValue()));
    }

    private TextBox textBox() { return textBox; }

    @Override
    public int getHeight() { return GuiTheme.SETTING_HEIGHT; }

    @Override
    public void drawScreen(GuiGraphics context, int mouseX, int mouseY, float partialTicks) {
        boolean hovering = isHovering(mouseX, mouseY);
        Color accent = Widget.currentAccent != null ? Widget.currentAccent : new Color(70, 75, 82);

        float x1 = this.x;
        float y1 = this.y;
        float x2 = this.x + this.width;
        float y2 = this.y + this.height - 1f;

        if (textBox.isFocused()) {
            textBox.renderPill(context, x1, y1, x2, y2, accent, hovering, 3f);
        } else {
            float ty = this.y + (this.height - 8) / 2f;
            String val = ChatFormatting.GRAY + this.setting.getValue();
            int w = Fonts.width(val);
            if (hovering) {
                textBox.renderPill(context, x1, y1, x2, y2, accent, true, 3f);
            }
            drawString(val, this.x + this.width - w - 3f, ty, GuiTheme.TEXT_SETTING_VALUE);
            int labelMax = Math.max(0, this.width - w - 8);
            drawScrollableString(this.setting.getName(), this.x + 2f, ty, GuiTheme.TEXT_SETTING, labelMax, hovering);
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && isHovering(mouseX, mouseY)) {
            if (!textBox.isFocused()) {
                textBox.setText(setting.getValue());
                textBox.focus();
            }
        } else if (!isHovering(mouseX, mouseY) && textBox.isFocused()) {
            textBox.unfocus();
        }
    }

    @Override
    public void keyActivate() {

        textBox.setText(setting.getValue());
        textBox.focus();
    }

    private void commit() {
        String s = textBox.getText();
        if (s.isEmpty()) {
            setting.setValue(setting.getDefaultValue());
        } else {
            setting.setValue(s);
        }
    }
}
