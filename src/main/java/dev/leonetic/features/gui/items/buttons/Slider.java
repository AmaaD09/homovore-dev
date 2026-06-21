package dev.leonetic.features.gui.items.buttons;

import dev.leonetic.features.gui.GuiTheme;
import dev.leonetic.features.gui.HomovoreGui;
import dev.leonetic.features.gui.Widget;
import dev.leonetic.features.gui.items.TextBox;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.render.RenderUtil;
import dev.leonetic.util.render.font.Fonts;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public class Slider extends SettingButton<Number> {
    private final Number min;
    private final Number max;
    private final float difference;
    private final TextBox textBox;

    public Slider(Setting<Number> setting) {
        super(setting);
        this.min = setting.getMin();
        this.max = setting.getMax();
        this.difference = this.max.floatValue() - this.min.floatValue();
        this.height = GuiTheme.SETTING_HEIGHT;
        this.textBox = new TextBox()
                .filter(this::acceptNumericChar)
                .maxLength(16)
                .onCommit(this::commitInput);
    }

    private boolean acceptNumericChar(int c) {
        if (Character.isDigit(c)) return true;
        String cur = textBox.getText();
        if (c == '.') return !cur.contains(".");
        if (c == '-') return cur.isEmpty();
        return false;
    }

    @Override
    public int getHeight() { return GuiTheme.SETTING_HEIGHT; }

    @Override
    public void drawScreen(GuiGraphics context, int mouseX, int mouseY, float partialTicks) {
        boolean hovering = this.isHovering(mouseX, mouseY);
        if (!textBox.isFocused()) {
            this.dragSetting(mouseX, mouseY);
        }

        Color accent = Widget.currentAccent != null ? Widget.currentAccent : new Color(120, 175, 220);
        float trackX1 = this.x;
        float trackX2 = this.x + this.width;
        float pct = partialMultiplier();
        float fillX = trackX1 + (trackX2 - trackX1) * pct;
        RenderUtil.rect(context, trackX1, this.y, fillX, this.y + this.height, accent.getRGB());
        if (hovering) {
            RenderUtil.rect(context, this.x, this.y, this.x + this.width, this.y + this.height,
                    GuiTheme.HIGHLIGHT_TOP);
        }

        float labelY = this.y + (this.height - 8) / 2f;
        float centerY = this.y + this.height / 2f;
        float valueRight = this.x + this.width - 3f;
        int valueColor = GuiTheme.TEXT_SETTING_VALUE;
        float valueLeft;
        if (textBox.isFocused()) {
            valueLeft = textBox.renderInlineRight(context, valueRight, centerY, GuiTheme.TEXT_MODULE_ON);
        } else {
            String value = ChatFormatting.GRAY + formatCurrentValue();
            int valWidth = Fonts.width(value);
            drawString(value, valueRight - valWidth, labelY, valueColor);
            valueLeft = valueRight - valWidth;
        }
        int labelMax = Math.max(0, (int) (valueLeft - (this.x + 2f) - 4f));
        drawScrollableString(this.getName(), this.x + 2f, labelY, GuiTheme.TEXT_SETTING, labelMax, hovering);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (textBox.isFocused()) {
            if (!this.isHovering(mouseX, mouseY)) textBox.unfocus();
            return;
        }
        if (mouseButton == 1 && this.isHovering(mouseX, mouseY)) {
            textBox.setText(formatCurrentValue());
            textBox.focus();
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.isHovering(mouseX, mouseY)) {
            this.setSettingFromX(mouseX);
        }
    }

    @Override
    public void keyActivate() {

        textBox.setText(formatCurrentValue());
        textBox.focus();
    }

    private void commitInput() {
        String input = textBox.getText();
        if (input.isEmpty() || input.equals("-")) return;
        try {
            if (setting.getValue() instanceof Double) {
                double val = Double.parseDouble(input);
                setting.setValue(Math.max(min.doubleValue(), Math.min(max.doubleValue(), val)));
            } else if (setting.getValue() instanceof Float) {
                float val = Float.parseFloat(input);
                setting.setValue(Math.max(min.floatValue(), Math.min(max.floatValue(), val)));
            } else if (setting.getValue() instanceof Integer) {
                int val = Integer.parseInt(input);
                setting.setValue(Math.max(min.intValue(), Math.min(max.intValue(), val)));
            }
        } catch (NumberFormatException ignored) {}
    }

    private String formatCurrentValue() {
        if (setting.getValue() instanceof Integer) return String.valueOf(setting.getValue().intValue());
        if (setting.getValue() instanceof Float) return setting.getValue().toString();
        return String.valueOf(setting.getValue().doubleValue());
    }

    @Override
    public boolean isHovering(int mouseX, int mouseY) {
        for (Widget widget : HomovoreGui.getClickGui().getComponents()) {
            if (widget.drag) return false;
        }
        return mouseX >= this.getX() && mouseX <= this.getX() + this.getWidth()
                && mouseY >= this.getY() && mouseY < this.getY() + this.height;
    }

    private void dragSetting(int mouseX, int mouseY) {
        if (this.isHovering(mouseX, mouseY) && GLFW.glfwGetMouseButton(mc.getWindow().handle(), 0) == 1) {
            this.setSettingFromX(mouseX);
        }
    }

    private void setSettingFromX(int mouseX) {
        float trackX1 = this.x;
        float trackX2 = this.x + this.width;
        float pct = Math.max(0, Math.min(1, (mouseX - trackX1) / (trackX2 - trackX1)));
        if (this.setting.getValue() instanceof Double) {
            double result = min.doubleValue() + difference * pct;
            this.setting.setValue(Math.round(10.0 * result) / 10.0);
        } else if (this.setting.getValue() instanceof Float) {
            float result = min.floatValue() + difference * pct;
            this.setting.setValue((float) Math.round(10.0f * result) / 10.0f);
        } else if (this.setting.getValue() instanceof Integer) {
            this.setting.setValue(min.intValue() + (int) (difference * pct));
        }
    }

    private float partialMultiplier() {
        if (difference == 0f) return 0f;
        return (setting.getValue().floatValue() - min.floatValue()) / difference;
    }
}
