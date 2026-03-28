package com.derko.swordthrow.mixin.client;

import com.derko.swordthrow.client.SwordThrowClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void swordthrow$redirectDropKeyToThrow(boolean dropAll, CallbackInfoReturnable<Boolean> cir) {
        if (dropAll) {
            return;
        }

        if (SwordThrowClient.consumeSingleItemDropBypass()) {
            return;
        }

        if (SwordThrowClient.shouldInterceptDropKey(Minecraft.getInstance())) {
            cir.setReturnValue(false);
        }
    }
}
