package dev.leonetic.mixin.client;

import dev.leonetic.util.render.font.Fonts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public abstract class MixinGuiGraphics {

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
            at = @At("HEAD"), cancellable = true)
    private void homovore$onDrawString(Font font, String text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
        if (text == null || text.isEmpty() || !Fonts.drawOverrideActive()) return;
        try {
            Fonts.renderOverrideString((GuiGraphics) (Object) this, text, x, y, color, shadow);
            ci.cancel();
        } catch (Exception ignored) {}
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
            at = @At("HEAD"), cancellable = true)
    private void homovore$onDrawFormatted(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
        if (text == null || !Fonts.drawOverrideActive()) return;
        try {
            Fonts.renderOverrideText((GuiGraphics) (Object) this, text, x, y, color, shadow);
            ci.cancel();
        } catch (Exception ignored) {}
    }
}
