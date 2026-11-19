package com.yztc.auth.fabric.mixin;

import com.yztc.auth.fabric.FabricEvents;
import com.yztc.auth.fabric.FabricTitles;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
    private void yztSuper$checkDrop(boolean wholeStack, CallbackInfoReturnable<Boolean> result) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (FabricEvents.locked(player)) {
            player.containerMenu.sendAllDataToRemote();
            result.setReturnValue(false);
        }
    }

    @Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true)
    private void yztSuper$tabTitle(CallbackInfoReturnable<Component> result) {
        Component titled = FabricTitles.display((ServerPlayer) (Object) this, result.getReturnValue());
        if (titled != null) result.setReturnValue(titled);
    }
}
