package com.derko.swordthrow;

import com.derko.swordthrow.entity.ModEntities;
import com.derko.swordthrow.entity.ThrownSwordEntity;
import com.derko.swordthrow.network.ThrowSwordPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwordThrowMod implements ModInitializer {
    public static final String MOD_ID = "swordthrow";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModEntities.register();
        PayloadTypeRegistry.playC2S().register(ThrowSwordPayload.ID, ThrowSwordPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ThrowSwordPayload.ID, (payload, context) ->
            context.server().execute(() -> tryThrowItem(context.player(), payload.chargeTicks()))
        );
        LOGGER.info("Sword Throw initialized");
    }

    private static void tryThrowItem(ServerPlayerEntity player, int rawChargeTicks) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return;
        }

        int chargeTicks = Math.max(0, Math.min(rawChargeTicks, 30));

        if (chargeTicks < 15) {
            return;
        }

        ItemStack held = player.getMainHandStack();
        if (held.isEmpty()) {
            return;
        }

        if (player.getItemCooldownManager().isCoolingDown(held)) {
            return;
        }

        ItemStack thrownStack = held.copy();
        if (!player.getAbilities().creativeMode) {
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        }

        float chargeProgress = chargeTicks / 30.0F;
        float throwSpeed = 1.10F + chargeProgress * 1.20F;

        ThrownSwordEntity projectile = new ThrownSwordEntity(player.getEntityWorld(), player, thrownStack);
        projectile.setVelocity(player, player.getPitch(), player.getYaw(), 0.0F, throwSpeed, 0.75F);
        player.getEntityWorld().spawnEntity(projectile);
        playThrowSound(player, projectile, chargeProgress);

        int cooldownTicks = 10 + Math.round(chargeProgress * 8.0F);
        player.getItemCooldownManager().set(thrownStack, cooldownTicks);
    }

    private static void playThrowSound(ServerPlayerEntity player, ThrownSwordEntity projectile, float chargeProgress) {
        float baseVolume = 0.45F + chargeProgress * 0.3F;
        float randomPitch = 0.92F + player.getRandom().nextFloat() * 0.12F;

        player.getEntityWorld().playSound(
            null,
            player.getX(),
            player.getY(),
            player.getZ(),
            SoundEvents.ENTITY_ARROW_SHOOT,
            SoundCategory.PLAYERS,
            baseVolume,
            randomPitch
        );

        if (projectile.usesPointFirstFlight()) {
            player.getEntityWorld().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.ITEM_TRIDENT_THROW,
                SoundCategory.PLAYERS,
                0.8F + chargeProgress * 0.25F,
                0.95F + player.getRandom().nextFloat() * 0.08F
            );
            return;
        }

        player.getEntityWorld().playSound(
            null,
            player.getX(),
            player.getY(),
            player.getZ(),
            SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
            SoundCategory.PLAYERS,
            0.18F + chargeProgress * 0.16F,
            0.8F + player.getRandom().nextFloat() * 0.15F
        );
    }
}
