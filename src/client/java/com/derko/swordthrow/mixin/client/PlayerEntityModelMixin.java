package com.derko.swordthrow.mixin.client;

import com.derko.swordthrow.client.config.SwordThrowClientConfig;
import com.derko.swordthrow.client.ThrowPoseState;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"))
    private void swordthrow$applyThirdPersonChargePose(PlayerEntityRenderState state, CallbackInfo ci) {
        if (!SwordThrowClientConfig.get().thirdPersonAnimationsEnabled()) {
            return;
        }

        if (state.preferredArm == null) {
            return;
        }

        Arm mainArm = state.preferredArm;
        Arm offArm = mainArm.getOpposite();
        ModelPart mainArmPart = ((BipedEntityModel<PlayerEntityRenderState>) (Object) this).getArm(mainArm);
        ModelPart offArmPart = ((BipedEntityModel<PlayerEntityRenderState>) (Object) this).getArm(offArm);
        ThrowPoseState.applyThirdPersonMainHandPose(state, mainArm, mainArmPart);
        ThrowPoseState.applyThirdPersonOffHandPose(state, offArm, offArmPart);
    }
}