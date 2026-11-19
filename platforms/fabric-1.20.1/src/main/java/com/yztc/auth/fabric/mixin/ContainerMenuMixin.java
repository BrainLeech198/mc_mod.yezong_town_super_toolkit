package com.yztc.auth.fabric.mixin;

import com.yztc.auth.fabric.FabricEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
abstract class ContainerMenuMixin {
    // 认证铁砧自行处理输入/结果槽；不允许其余原版槽位改物品或向窗口外抛物。
    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void yztSuper$checkInventoryClick(int slot, int button, ClickType click, Player player, CallbackInfo ci) {
        if (FabricEvents.locked(player)) {
            player.containerMenu.sendAllDataToRemote();
            ci.cancel();
        }
    }
}
