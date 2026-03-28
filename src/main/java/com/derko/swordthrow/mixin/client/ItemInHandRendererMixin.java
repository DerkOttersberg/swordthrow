package com.derko.swordthrow.mixin.client;

import com.derko.swordthrow.client.ThrowPoseState;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Method;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    private static Method swordthrow$renderPlayerArmMethod;

    @Inject(
        method = "renderArmWithItem",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER)
    )
    private void swordthrow$applyThrowPose(
        AbstractClientPlayer player,
        float partialTick,
        float pitch,
        InteractionHand hand,
        float swingProgress,
        ItemStack item,
        float equipProgress,
        PoseStack poseStack,
        @Coerce Object nodeCollector,
        int packedLight,
        CallbackInfo ci
    ) {
        if (player.isInvisible()) {
            return;
        }

        if (hand == InteractionHand.MAIN_HAND && !item.isEmpty()) {
            ThrowPoseState.applyMainHandPose(player, poseStack, partialTick);
            return;
        }

        if (hand == InteractionHand.OFF_HAND && item.isEmpty() && ThrowPoseState.isOffHandVisible()) {
            HumanoidArm offArm = player.getMainArm().getOpposite();
            ThrowPoseState.applyOffHandAimContext(player, poseStack, offArm, partialTick);
            swordthrow$invokeRenderPlayerArmReflectively(poseStack, nodeCollector, packedLight, swingProgress, offArm);
        }
    }

    private void swordthrow$invokeRenderPlayerArmReflectively(
        PoseStack poseStack,
        Object nodeCollector,
        int packedLight,
        float swingProgress,
        HumanoidArm arm
    ) {
        try {
            Method method = swordthrow$renderPlayerArmMethod;
            if (method == null) {
                for (Method declaredMethod : ItemInHandRenderer.class.getDeclaredMethods()) {
                    if (declaredMethod.getName().equals("renderPlayerArm") && declaredMethod.getParameterCount() == 6) {
                        declaredMethod.setAccessible(true);
                        swordthrow$renderPlayerArmMethod = declaredMethod;
                        method = declaredMethod;
                        break;
                    }
                }
            }

            if (method != null) {
                method.invoke(this, poseStack, nodeCollector, packedLight, 0.0F, swingProgress, arm);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
