package com.derko.swordthrow.mixin.client;

import com.derko.swordthrow.client.ThrowPoseState;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {
    @Shadow @Final public ModelPart leftSleeve;
    @Shadow @Final public ModelPart rightSleeve;

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"))
    private void swordthrow$applyThirdPersonOffHandChargePose(PlayerEntityRenderState state, CallbackInfo ci) {
        if (state.preferredArm == null) {
            return;
        }

        Arm offArm = state.preferredArm;
        Arm mainArm = offArm.getOpposite();
        ModelPart mainArmPart = ((BipedEntityModel<PlayerEntityRenderState>) (Object) this).getArm(mainArm);
        ModelPart offArmPart = ((BipedEntityModel<PlayerEntityRenderState>) (Object) this).getArm(offArm);
        ModelPart mainSleevePart = mainArm == Arm.RIGHT ? this.rightSleeve : this.leftSleeve;
        ModelPart offSleevePart = offArm == Arm.RIGHT ? this.rightSleeve : this.leftSleeve;
        ThrowPoseState.applyThirdPersonMainHandPose(state, mainArm, mainArmPart, mainSleevePart);
        ThrowPoseState.applyThirdPersonOffHandPose(state, offArm, offArmPart, offSleevePart);
    }
}