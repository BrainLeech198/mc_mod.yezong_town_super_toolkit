package com.yztc.auth.fabric.mixin;

import com.yztc.auth.auth.PlayerLifecycle;
import com.yztc.auth.fabric.FabricTitles;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
abstract class PlayerListMixin {
    // Fabric JOIN 在原版的初始玩家列表与背包同步之前触发。此处可安全发锁定包及隐藏 Tab。
    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void yztSuper$join(Connection connection, ServerPlayer player, CallbackInfo ci) {
        PlayerLifecycle.joined(player);
        FabricTitles.sendNameplatesTo(player);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void yztSuper$leave(ServerPlayer player, CallbackInfo ci) {
        PlayerLifecycle.left(player);
        FabricTitles.removeNameplate(player);
    }
}
