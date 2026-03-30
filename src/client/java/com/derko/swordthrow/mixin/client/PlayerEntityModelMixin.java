package com.derko.swordthrow.mixin.client;

import com.derko.swordthrow.client.config.SwordThrowClientConfig;
import com.derko.swordthrow.client.ThrowPoseState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {
    @Inject(method = "setAngles", at = @At("TAIL"), require = 0)
    private void swordthrow$applyThirdPersonChargePose(CallbackInfo ci) {
        if (!SwordThrowClientConfig.get().thirdPersonAnimationsEnabled()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        float animationProgress = client.player.age + client.getRenderTickCounter().getTickDelta(true);
        Arm mainArm = client.player.getMainArm();
        Arm offArm = mainArm.getOpposite();
        BipedEntityModelAccessor armAccessor = (BipedEntityModelAccessor) (Object) this;
        PlayerEntityModelAccessor playerModelAccessor = (PlayerEntityModelAccessor) (Object) this;
        ModelPart mainArmPart = armAccessor.swordthrow$getArm(mainArm);
        ModelPart offArmPart = armAccessor.swordthrow$getArm(offArm);
        ThrowPoseState.applyThirdPersonMainHandPose(animationProgress, mainArm, mainArmPart);
        ThrowPoseState.applyThirdPersonOffHandPose(animationProgress, offArm, offArmPart);

        ModelPart rightArmPart = armAccessor.swordthrow$getArm(Arm.RIGHT);
        ModelPart leftArmPart = armAccessor.swordthrow$getArm(Arm.LEFT);
        playerModelAccessor.swordthrow$getRightSleeve().copyTransform(rightArmPart);
        playerModelAccessor.swordthrow$getLeftSleeve().copyTransform(leftArmPart);
    }
}