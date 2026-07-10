package dev.leonetic.mixin.client;

import dev.leonetic.features.modules.render.ShulkerPreviewModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class MixinAbstractContainerScreen<T extends AbstractContainerMenu> {

    @Shadow
    @Final
    protected T menu;

    @Shadow
    protected Slot hoveredSlot;

    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void onRenderShulkerPreview(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        ShulkerPreviewModule module = ShulkerPreviewModule.get();
        if (module == null || !module.isEnabled()) return;
        if (hoveredSlot == null || !hoveredSlot.hasItem()) return;
        if (!menu.getCarried().isEmpty()) return;

        if (module.renderPreview(graphics, hoveredSlot.getItem(), mouseX, mouseY)) {
            ci.cancel();
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void onMouseDragged(MouseButtonEvent mouseButtonEvent, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getAbilities().instabuild) return;

        ItemStack cursorStack = menu.getCarried();
        if (cursorStack == null || cursorStack.isEmpty()) return;
        if (!cursorStack.isStackable() || cursorStack.getItem() instanceof MapItem || cursorStack.getItem() instanceof BannerItem) {
            cir.setReturnValue(true);
        }
    }
}
