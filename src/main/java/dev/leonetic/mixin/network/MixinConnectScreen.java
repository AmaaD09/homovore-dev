package dev.leonetic.mixin.network;

import dev.leonetic.features.modules.client.ConnectorModule;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ConnectScreen.class)
public class MixinConnectScreen {

    @ModifyVariable(
            method = "startConnecting(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/multiplayer/resolver/ServerAddress;Lnet/minecraft/client/multiplayer/ServerData;ZLnet/minecraft/client/multiplayer/TransferState;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private static ServerAddress homovore$rewriteJoinAddress(ServerAddress originalAddress) {
        ConnectorModule module = ConnectorModule.getInstance();
        return module != null ? module.rewriteAddress(originalAddress) : originalAddress;
    }
}
