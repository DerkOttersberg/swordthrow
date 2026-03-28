package com.derko.swordthrow.mixin.client;

import com.derko.swordthrow.client.ThrowPoseState;
import com.derko.swordthrow.client.config.SwordThrowClientConfig;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import java.lang.reflect.Field;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
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

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void swordthrow$applyThirdPersonChargePose(
        LivingEntity entity,
        float limbSwing,
        float limbSwingAmount,
        float ageInTicks,
        float netHeadYaw,
        float headPitch,
        CallbackInfo ci
    ) {
        if (!SwordThrowClientConfig.get().thirdPersonAnimationsEnabled()) {
            return;
        }

        HumanoidArm mainArm = entity.getMainArm();
        if (mainArm == null) {
            return;
        }

        HumanoidArm offArm = mainArm.getOpposite();
        ThrowPoseState.applyThirdPersonMainHandPose(
            ageInTicks,
            mainArm,
            swordthrow$getArm(mainArm)
        );
        ThrowPoseState.applyThirdPersonOffHandPose(
            ageInTicks,
            offArm,
            swordthrow$getArm(offArm)
        );

        swordthrow$getSleeve(HumanoidArm.RIGHT).copyFrom(swordthrow$getArm(HumanoidArm.RIGHT));
        swordthrow$getSleeve(HumanoidArm.LEFT).copyFrom(swordthrow$getArm(HumanoidArm.LEFT));
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
