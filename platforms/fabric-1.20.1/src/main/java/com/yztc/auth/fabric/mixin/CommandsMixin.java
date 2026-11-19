package com.yztc.auth.fabric.mixin;

import com.mojang.brigadier.ParseResults;
import com.yztc.auth.fabric.FabricEvents;
import com.yztc.auth.lang.Lang;
import com.yztc.auth.lockdown.LockdownRules;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Commands.class)
abstract class CommandsMixin {
    @Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
    private void yztSuper$checkCommand(ParseResults<CommandSourceStack> parsed, String command,
                                       CallbackInfoReturnable<Integer> result) {
        ServerPlayer player = parsed.getContext().getSource().getPlayer();
        if (FabricEvents.locked(player) && !LockdownRules.allowedWhileLocked(parsed.getReader().getString())) {
            player.sendSystemMessage(Lang.msg("need_login"));
            result.setReturnValue(0);
        }
    }
}
