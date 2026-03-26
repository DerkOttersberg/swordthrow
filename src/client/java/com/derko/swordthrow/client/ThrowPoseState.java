package com.derko.swordthrow.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public final class ThrowPoseState {
    private static final int RELEASE_TICKS = 6;

    private static float previousChargeProgress;
    private static float chargeProgress;
    private static int releaseTicksRemaining;

    private ThrowPoseState() {
    }

    public static void beginCharge() {
        previousChargeProgress = 0.0F;
        chargeProgress = 0.0F;
        releaseTicksRemaining = 0;
    }

    public static void setChargeProgress(float progress) {
        previousChargeProgress = chargeProgress;
        chargeProgress = MathHelper.clamp(progress, 0.0F, 1.0F);
    }

    public static void releaseForward() {
        releaseTicksRemaining = RELEASE_TICKS;
    }

    public static void cancel() {
        previousChargeProgress = 0.0F;
        chargeProgress = 0.0F;
        releaseTicksRemaining = 0;
    }

    public static void tick() {
        if (releaseTicksRemaining > 0) {
            releaseTicksRemaining--;
            if (releaseTicksRemaining == 0) {
                previousChargeProgress = 0.0F;
                chargeProgress = 0.0F;
            }
        }
    }

    /** Visible while charging and during the short off-hand recoil after release. */
    public static boolean isOffHandVisible() {
        return chargeProgress > 0.001F || releaseTicksRemaining > 0;
    }

    /**
     * Main-hand pose applied at TAIL of renderFirstPersonItem (inside a push/pop scope).
     * Wind-up: sword arm rises UP and pulls BACK above the shoulder.
     * Release: arm drives sharply FORWARD and DOWN — overhand follow-through.
     */
    public static void applyMainHandPose(AbstractClientPlayerEntity player, MatrixStack matrices, float tickDelta) {
        float interpolatedCharge = getChargeProgress(tickDelta);
        boolean hasCharge = interpolatedCharge > 0.001F;
        boolean releasing = releaseTicksRemaining > 0;
        if (!hasCharge && !releasing) return;

        float easedCharge = interpolatedCharge * interpolatedCharge;

        float releaseProgress = 0.0F;
        if (releasing) {
            float raw = 1.0F - ((releaseTicksRemaining - tickDelta) / RELEASE_TICKS);
            float clamped = MathHelper.clamp(raw, 0.0F, 1.0F);
            releaseProgress = 1.0F - (1.0F - clamped) * (1.0F - clamped); // ease-out
        }

        float windAmount = easedCharge * (1.0F - releaseProgress);
        float snap = releaseProgress;

        Arm handArm = player.getMainArm();
        float side = handArm == Arm.RIGHT ? 1.0F : -1.0F;

        // Wind-up  : arm moves UP (Y+) and BACK (Z+) and slightly to the throwing side
        // Release  : arm whips FORWARD (Z-) and slightly DOWN — stop around hip level
        matrices.translate(
            side * 0.08F * windAmount - side * 0.02F * snap,
            0.30F * windAmount - 0.05F * snap,
            0.18F * windAmount - 0.55F * snap
        );
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (10.0F * windAmount - 4.0F * snap)));
        // -130° = arm tipped well above horizontal (near-vertical overhead)
        // +60°  = arm past neutral (pointing slightly down-forward after the throw)
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-130.0F * windAmount + 60.0F * snap));
    }

    /**
     * Off-hand aiming context used both while charging and during the brief release recoil.
     * Charging moves the arm forward toward the target; release snaps it back quickly.
     */
    public static void applyOffHandAimContext(MatrixStack matrices, Arm offArm, float tickDelta) {
        float interpolatedCharge = getChargeProgress(tickDelta);
        float windAmount = interpolatedCharge * interpolatedCharge;
        float releaseProgress = 0.0F;
        if (releaseTicksRemaining > 0) {
            float raw = 1.0F - ((releaseTicksRemaining - tickDelta) / RELEASE_TICKS);
            float clamped = MathHelper.clamp(raw, 0.0F, 1.0F);
            releaseProgress = 1.0F - (1.0F - clamped) * (1.0F - clamped);
        }

        if (windAmount < 0.001F && releaseProgress < 0.001F) return;

        float side = offArm == Arm.RIGHT ? 1.0F : -1.0F;
        float extend = windAmount * (1.0F - releaseProgress);
        float recoil = releaseProgress;

        // Charge: move arm inward and forward to aim.
        // Release: pull it back out and down in a quick recoil.
        matrices.translate(
            -side * 0.22F * extend + side * 0.18F * recoil,
            0.06F * extend - 0.10F * recoil,
            -0.15F * extend + 0.24F * recoil
        );
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (15.0F * extend - 18.0F * recoil)));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-18.0F * extend + 24.0F * recoil));
    }

    private static float getChargeProgress(float tickDelta) {
        return MathHelper.lerp(tickDelta, previousChargeProgress, chargeProgress);
    }
}
