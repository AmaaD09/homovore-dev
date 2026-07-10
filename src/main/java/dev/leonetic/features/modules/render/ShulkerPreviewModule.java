package dev.leonetic.features.modules.render;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.awt.Color;

public class ShulkerPreviewModule extends Module {

    private static final int COLUMNS = 9;
    private static final int ROWS = 3;
    private static final int SLOTS = COLUMNS * ROWS;
    private static final int CELL = 18;
    private static final int PADDING = 3;
    private static final int BORDER = 1;
    private static final int CURSOR_GAP = 12;
    private static final int SCREEN_MARGIN = 2;

    public final Setting<Color> background = color("Background", 16, 16, 20, 240);
    public final Setting<Color> border = color("Border", 130, 80, 255, 255);
    public final Setting<Boolean> hideEmpty = bool("HideEmpty", false);

    public ShulkerPreviewModule() {
        super("ShulkerPreview", "Shows the contents of a shulker box when you hover it", Category.RENDER);
    }

    public static ShulkerPreviewModule get() {
        if (Homovore.moduleManager == null) return null;
        return Homovore.moduleManager.getModuleByClass(ShulkerPreviewModule.class);
    }

    public boolean renderPreview(GuiGraphics graphics, ItemStack stack, int mouseX, int mouseY) {
        if (!isShulkerBox(stack)) return false;

        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) return false;

        NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        contents.copyInto(items);

        if (hideEmpty.getValue() && items.stream().allMatch(ItemStack::isEmpty)) return false;

        int width = COLUMNS * CELL + PADDING * 2;
        int height = ROWS * CELL + PADDING * 2;

        int x = clamp(mouseX + CURSOR_GAP, SCREEN_MARGIN, graphics.guiWidth() - width - SCREEN_MARGIN);
        int y = clamp(mouseY - CURSOR_GAP, SCREEN_MARGIN, graphics.guiHeight() - height - SCREEN_MARGIN);

        graphics.nextStratum();
        graphics.fill(x - BORDER, y - BORDER, x + width + BORDER, y + height + BORDER, border.getValue().getRGB());
        graphics.fill(x, y, x + width, y + height, background.getValue().getRGB());

        for (int i = 0; i < SLOTS; i++) {
            ItemStack item = items.get(i);
            if (item.isEmpty()) continue;

            int ix = x + PADDING + (i % COLUMNS) * CELL + 1;
            int iy = y + PADDING + (i / COLUMNS) * CELL + 1;
            graphics.renderItem(item, ix, iy);
            graphics.renderItemDecorations(mc.font, item, ix, iy);
        }

        return true;
    }

    public static boolean isShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }
}
