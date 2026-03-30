package com.derko.swordthrow.mixin.client;

import com.derko.swordthrow.client.ThrowPoseState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Invoker("renderArmHoldingItem")
    protected abstract void swordthrow$invokeRenderArmHoldingItem(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float equipProgress, float swingProgress, Arm arm);

    @Inject(
        method = "renderFirstPersonItem",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER)
    )
    private void swordthrow$applyThrowPoseInScope(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (player.isInvisible()) return;

        if (hand == Hand.MAIN_HAND && !item.isEmpty()) {
            ThrowPoseState.applyMainHandPose(player, matrices, tickDelta);
            return;
        }

        if (hand == Hand.OFF_HAND && item.isEmpty() && ThrowPoseState.isOffHandVisible()) {
            Arm aimArm = player.getMainArm().getOpposite();
            ThrowPoseState.applyOffHandAimContext(player, matrices, aimArm, tickDelta);
            this.swordthrow$invokeRenderArmHoldingItem(matrices, vertexConsumers, light, 0.0F, swingProgress, aimArm);
        }
    }
}
