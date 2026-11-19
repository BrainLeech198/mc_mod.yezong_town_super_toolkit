package com.yztc.auth.fabric.mixin;

import com.yztc.auth.fabric.FabricEvents;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class CreativeSlotMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
    private void yztSuper$checkCreativeSlot(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        // 第一次调用可能在网络线程，交给原版排队；主线程再次处理时才可读认证/背包状态。
        if (player.server.isSameThread() && FabricEvents.locked(player)) {
            player.containerMenu.sendAllDataToRemote();
            ci.cancel();
        }
    }
}
