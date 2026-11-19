package com.yztc.auth.fabric.mixin;

import com.yztc.auth.fabric.FabricTitles;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
abstract class PlayerDisplayNameMixin {
    // 原版聊天类型格式化发件人；保留签名正文与原版/队伍样式，只装饰玩家名。
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void yztSuper$displayTitle(CallbackInfoReturnable<Component> result) {
        if ((Object) this instanceof ServerPlayer player) {
            Component titled = FabricTitles.display(player, result.getReturnValue());
            if (titled != null) result.setReturnValue(titled);
        }
    }
}
