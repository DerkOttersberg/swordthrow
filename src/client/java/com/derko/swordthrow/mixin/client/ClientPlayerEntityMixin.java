package com.derko.swordthrow.mixin.client;

import com.derko.swordthrow.client.SwordThrowClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {

    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void swordthrow$redirectDropKeyToThrow(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        if (entireStack) {
            return;
        }

        if (SwordThrowClient.shouldInterceptDropKey(MinecraftClient.getInstance())) {
            cir.setReturnValue(false);
        }
    }
}