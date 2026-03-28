package com.derko.swordthrow.mixin.client;

import com.derko.swordthrow.client.ThrowPoseState;
import com.derko.swordthrow.client.config.SwordThrowClientConfig;
import java.lang.reflect.Field;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    private static Field swordthrow$rightArmField;
    private static Field swordthrow$leftArmField;
    private static Field swordthrow$rightSleeveField;
    private static Field swordthrow$leftSleeveField;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void swordthrow$applyThirdPersonChargePose(
        AvatarRenderState state,
        CallbackInfo ci
    ) {
        if (!SwordThrowClientConfig.get().thirdPersonAnimationsEnabled()) {
            return;
        }

        HumanoidArm mainArm = state.mainArm;
        if (mainArm == null) {
            return;
        }

        HumanoidArm offArm = mainArm.getOpposite();
        ThrowPoseState.applyThirdPersonMainHandPose(
            state.ageInTicks,
            mainArm,
            swordthrow$getArm(mainArm)
        );
        ThrowPoseState.applyThirdPersonOffHandPose(
            state.ageInTicks,
            offArm,
            swordthrow$getArm(offArm)
        );

        swordthrow$copyPartTransform(swordthrow$getSleeve(HumanoidArm.RIGHT), swordthrow$getArm(HumanoidArm.RIGHT));
        swordthrow$copyPartTransform(swordthrow$getSleeve(HumanoidArm.LEFT), swordthrow$getArm(HumanoidArm.LEFT));
    }

    private static void swordthrow$copyPartTransform(ModelPart target, ModelPart source) {
        target.x = source.x;
        target.y = source.y;
        target.z = source.z;
        target.xRot = source.xRot;
        target.yRot = source.yRot;
        target.zRot = source.zRot;
        target.xScale = source.xScale;
        target.yScale = source.yScale;
        target.zScale = source.zScale;
        target.visible = source.visible;
        target.skipDraw = source.skipDraw;
    }

    private ModelPart swordthrow$getArm(HumanoidArm side) {
        try {
            if (swordthrow$rightArmField == null || swordthrow$leftArmField == null) {
                swordthrow$rightArmField = HumanoidModel.class.getDeclaredField("rightArm");
                swordthrow$leftArmField = HumanoidModel.class.getDeclaredField("leftArm");
                swordthrow$rightArmField.setAccessible(true);
                swordthrow$leftArmField.setAccessible(true);
            }

            return (ModelPart)(side == HumanoidArm.RIGHT ? swordthrow$rightArmField : swordthrow$leftArmField).get(this);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to access player arm model part", ex);
        }
    }

    private ModelPart swordthrow$getSleeve(HumanoidArm side) {
        try {
            if (swordthrow$rightSleeveField == null || swordthrow$leftSleeveField == null) {
                swordthrow$rightSleeveField = PlayerModel.class.getDeclaredField("rightSleeve");
                swordthrow$leftSleeveField = PlayerModel.class.getDeclaredField("leftSleeve");
                swordthrow$rightSleeveField.setAccessible(true);
                swordthrow$leftSleeveField.setAccessible(true);
            }

            return (ModelPart)(side == HumanoidArm.RIGHT ? swordthrow$rightSleeveField : swordthrow$leftSleeveField).get(this);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to access player sleeve model part", ex);
        }
    }
}
