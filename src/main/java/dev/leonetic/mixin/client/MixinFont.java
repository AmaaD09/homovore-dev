package dev.leonetic.mixin.client;

import dev.leonetic.util.render.font.Fonts;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Font.class)
public class MixinFont {

    @Inject(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
    private void homovore$widthString(String text, CallbackInfoReturnable<Integer> cir) {
        if (text == null || !Fonts.drawOverrideActive()) return;
        try {
            cir.setReturnValue(Fonts.overrideWidth(text));
        } catch (Exception ignored) {}
    }

    @Inject(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), cancellable = true)
    private void homovore$widthFormatted(FormattedCharSequence text, CallbackInfoReturnable<Integer> cir) {
        if (text == null || !Fonts.drawOverrideActive()) return;
        try {
            cir.setReturnValue(Fonts.overrideWidth(text));
        } catch (Exception ignored) {}
    }

    @Inject(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("HEAD"), cancellable = true)
    private void homovore$widthFormattedText(FormattedText text, CallbackInfoReturnable<Integer> cir) {
        if (text == null || !Fonts.drawOverrideActive()) return;
        try {
            cir.setReturnValue(Fonts.overrideWidth(Language.getInstance().getVisualOrder(text)));
        } catch (Exception ignored) {}
    }
}
