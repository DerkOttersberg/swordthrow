package com.derko.swordthrow;

import com.derko.swordthrow.client.SwordThrowClient;
import com.derko.swordthrow.client.config.SwordThrowConfigScreen;
import com.derko.swordthrow.entity.ModEntities;
import com.derko.swordthrow.entity.ThrownSwordEntity;
import com.derko.swordthrow.network.ThrowSwordPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(SwordThrowMod.MOD_ID)
public class SwordThrowMod {
    public static final String MOD_ID = "swordthrow";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public SwordThrowMod(IEventBus modEventBus, ModContainer container) {
        ModEntities.register(modEventBus);
        modEventBus.addListener(this::registerPayloads);

        if (FMLEnvironment.getDist().isClient()) {
            container.registerExtensionPoint(IConfigScreenFactory.class, (modContainer, parent) -> new SwordThrowConfigScreen(parent));
            SwordThrowClient.init(modEventBus);
        }

        LOGGER.info("Sword Throw (NeoForge) initialized");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(ThrowSwordPayload.TYPE, ThrowSwordPayload.STREAM_CODEC, (payload, context) ->
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    tryThrowItem(player, payload.chargeTicks());
                }
            })
        );
    }

    public static void tryThrowItem(ServerPlayer player, int rawChargeTicks) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return;
        }

        int chargeTicks = Math.max(0, Math.min(rawChargeTicks, 30));
        if (chargeTicks < 15) {
            return;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            return;
        }

        if (player.getCooldowns().isOnCooldown(held)) {
            return;
        }

        ItemStack thrownStack = held.copy();
        if (!player.getAbilities().instabuild) {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }

        float chargeProgress = chargeTicks / 30.0F;
        float throwSpeed = 1.10F + chargeProgress * 1.20F;

        ThrownSwordEntity projectile = new ThrownSwordEntity(player.level(), player, thrownStack);
        projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, throwSpeed, 0.75F);
        player.level().addFreshEntity(projectile);
        playThrowSound(player, projectile, chargeProgress);

        int cooldownTicks = 10 + Math.round(chargeProgress * 8.0F);
        player.getCooldowns().addCooldown(thrownStack, cooldownTicks);
    }

    private static void playThrowSound(ServerPlayer player, ThrownSwordEntity projectile, float chargeProgress) {
        float baseVolume = 0.45F + chargeProgress * 0.3F;
        float randomPitch = 0.92F + player.getRandom().nextFloat() * 0.12F;

        player.level().playSound(
            null,
            player.getX(),
            player.getY(),
            player.getZ(),
            SoundEvents.ARROW_SHOOT,
            SoundSource.PLAYERS,
            baseVolume,
            randomPitch
        );

        if (projectile.usesPointFirstFlight()) {
            player.level().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.TRIDENT_THROW,
                SoundSource.PLAYERS,
                0.8F + chargeProgress * 0.25F,
                0.95F + player.getRandom().nextFloat() * 0.08F
            );
            return;
        }

        player.level().playSound(
            null,
            player.getX(),
            player.getY(),
            player.getZ(),
            SoundEvents.PLAYER_ATTACK_SWEEP,
            SoundSource.PLAYERS,
            0.18F + chargeProgress * 0.16F,
            0.8F + player.getRandom().nextFloat() * 0.15F
        );
    }
}
