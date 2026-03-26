package com.derko.swordthrow.mixin.client;

import com.derko.swordthrow.client.ThrowPoseState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
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
    protected abstract void swordthrow$invokeRenderArmHoldingItem(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, float equipProgress, float swingProgress, Arm arm);

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void swordthrow$applyThrowPose(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        ThrowPoseState.applyFirstPersonPose(player, hand, matrices, tickDelta);
    }

    @Inject(method = "renderFirstPersonItem", at = @At("TAIL"))
    private void swordthrow$renderSwordArm(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        if (item.isEmpty() || !item.isIn(ItemTags.SWORDS) || player.isInvisible()) {
            return;
        }

        Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        this.swordthrow$invokeRenderArmHoldingItem(matrices, queue, light, equipProgress, swingProgress, arm);
    }
}
