package com.derko.swordthrow.entity;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

public class ThrownSwordEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Boolean> DATA_EMBEDDED = SynchedEntityData.defineId(ThrownSwordEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_EMBEDDED_ROLL = SynchedEntityData.defineId(ThrownSwordEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_EMBEDDED_YAW = SynchedEntityData.defineId(ThrownSwordEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_EMBEDDED_PITCH = SynchedEntityData.defineId(ThrownSwordEntity.class, EntityDataSerializers.FLOAT);
    private static final double BOUNCE_DAMPING = 0.68D;
    private static final double WALL_HORIZONTAL_DAMPING = 0.42D;
    private static final double WALL_VERTICAL_DAMPING = 0.92D;
    private static final double MIN_BOUNCE_SPEED_SQUARED = 0.18D * 0.18D;
    private static final double BOUNCE_SURFACE_OFFSET = 0.08D;
    private static final double EMBED_DEPTH = 0.12D;
    private static final double EMBED_SURFACE_INSET = 0.02D;
    private static final double EMBED_MIN_SPEED_SQUARED = 0.42D * 0.42D;
    private static final double EMBED_HEAD_ON_THRESHOLD = 0.24D;
    private static final float EMBEDDED_ROLL_THRESHOLD = 0.38F;
    private static final int MAX_TRAIL_POINTS = 20;
    private static final float BASE_HAND_DAMAGE = 1.0F;
    private static final float VELOCITY_DAMAGE_FACTOR = 0.7F;
    private static final float VELOCITY_DAMAGE_BASE = 0.65F;
    private static final float CLEAN_FLIGHT_DAMAGE_BONUS = 1.35F;
    private static final float SPEAR_DAMAGE_MULTIPLIER = 1.45F;
    private static final float SPEAR_DAMAGE_FLAT_BONUS = 2.5F;
    private static final float FLIGHT_SPIN_SPEED = 34.0F;
    private static final float BLOCK_BASE_DAMAGE = 0.35F;
    private static final float MISC_BASE_DAMAGE = 0.6F;
    private static final String STACK_COUNT_KEY = "ThrownStackCount";
    private static final String EMBEDDED_KEY = "Embedded";
    private static final String EMBEDDED_ROLL_KEY = "EmbeddedRoll";
    private static final String EMBEDDED_YAW_KEY = "EmbeddedYaw";
    private static final String EMBEDDED_PITCH_KEY = "EmbeddedPitch";
    private static final String EMBEDDED_X_KEY = "EmbeddedX";
    private static final String EMBEDDED_Y_KEY = "EmbeddedY";
    private static final String EMBEDDED_Z_KEY = "EmbeddedZ";

    private boolean dropped;
    private boolean hitBlock;
    private int thrownStackCount = 1;
    private Vec3 embeddedPosition = Vec3.ZERO;
    private final Deque<Vec3> trailPoints = new ArrayDeque<>();

    public ThrownSwordEntity(EntityType<? extends ThrownSwordEntity> entityType, Level level) {
        super(entityType, level);
    }

    public ThrownSwordEntity(Level level, LivingEntity owner, ItemStack stack) {
        super(ModEntities.THROWN_SWORD.get(), owner, level, stack.copyWithCount(1));
        this.thrownStackCount = Math.max(1, stack.getCount());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_EMBEDDED, false);
        builder.define(DATA_EMBEDDED_ROLL, 0.0F);
        builder.define(DATA_EMBEDDED_YAW, 0.0F);
        builder.define(DATA_EMBEDDED_PITCH, 0.0F);
    }

    @Override
    public void tick() {
        if (this.isEmbedded()) {
            this.tickCount++;
            this.setNoGravity(true);
            this.setDeltaMovement(Vec3.ZERO);
            if (!this.level().isClientSide()) {
                this.setPos(this.embeddedPosition);
            } else {
                this.embeddedPosition = this.position();
            }

            float embeddedYaw = this.getEmbeddedYaw();
            float embeddedPitch = this.getEmbeddedPitch();
            this.setYRot(embeddedYaw);
            this.setXRot(embeddedPitch);
            this.yRotO = embeddedYaw;
            this.xRotO = embeddedPitch;

            if (this.level().isClientSide()) {
                this.trailPoints.clear();
                this.trailPoints.addLast(this.position());
            }
            return;
        }

        super.tick();

        if (this.level().isClientSide()) {
            recordTrailPoint();
            return;
        }

        if (this.level().isClientSide()) {
            return;
        }

        this.setDeltaMovement(this.getDeltaMovement().multiply(0.985D, 0.97D, 0.985D));

        if (this.tickCount > 120) {
            this.dropAsItemAndDiscard();
        }
    }

    @Override
    protected double getDefaultGravity() {
        return 0.06D;
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        if (this.isEmbedded()) {
            return;
        }

        super.onHitEntity(hitResult);

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        DamageSource source = this.damageSources().thrown(this, this.getOwner());
        float damage = this.getThrownDamage(serverLevel, this.getItem(), hitResult.getEntity(), source);
        hitResult.getEntity().hurt(source, damage);
        this.applyThrownHitEffects(serverLevel, this.getItem(), hitResult, source);
        this.dropAsItemAndDiscard();
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        if (this.isEmbedded()) {
            return;
        }

        super.onHitBlock(hitResult);

        if (this.level().isClientSide()) {
            return;
        }

        Vec3 velocity = this.getDeltaMovement();
        Vec3 normal = hitResult.getDirection().getUnitVec3();
        boolean firstBlockHit = !this.hitBlock;
        this.hitBlock = true;

        if (firstBlockHit && this.canEmbedInBlock() && this.shouldEmbedInBlock(velocity, normal)) {
            this.embedInBlock(hitResult, velocity, normal);
            return;
        }

        double normalComponent = velocity.dot(normal);
        Vec3 reflectedVelocity = velocity.subtract(normal.scale(2.0D * normalComponent)).scale(BOUNCE_DAMPING);

        if (hitResult.getDirection().getAxis().isHorizontal()) {
            reflectedVelocity = new Vec3(
                reflectedVelocity.x * WALL_HORIZONTAL_DAMPING,
                reflectedVelocity.y * WALL_VERTICAL_DAMPING,
                reflectedVelocity.z * WALL_HORIZONTAL_DAMPING
            );
        }

        if (reflectedVelocity.lengthSqr() < MIN_BOUNCE_SPEED_SQUARED) {
            this.dropAsItemAndDiscard();
            return;
        }

        this.setPos(hitResult.getLocation().add(normal.scale(BOUNCE_SURFACE_OFFSET)));
        this.setDeltaMovement(reflectedVelocity);

        if (this.level() instanceof ServerLevel serverLevel) {
            this.playBounceSound(serverLevel, hitResult, reflectedVelocity.lengthSqr());
        }
    }

    @Override
    protected Item getDefaultItem() {
        return Items.IRON_SWORD;
    }

    public List<Vec3> getTrailPoints() {
        if (this.trailPoints.isEmpty()) {
            return Collections.emptyList();
        }
        return List.copyOf(this.trailPoints);
    }

    public boolean isEmbedded() {
        return this.entityData.get(DATA_EMBEDDED);
    }

    public float getEmbeddedRoll() {
        return this.entityData.get(DATA_EMBEDDED_ROLL);
    }

    public float getEmbeddedYaw() {
        return this.entityData.get(DATA_EMBEDDED_YAW);
    }

    public float getEmbeddedPitch() {
        return this.entityData.get(DATA_EMBEDDED_PITCH);
    }

    public float getSpinPhaseOffsetDegrees() {
        return (this.getId() * 57.0F) % 360.0F;
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt(STACK_COUNT_KEY, this.thrownStackCount);
        output.putBoolean(EMBEDDED_KEY, this.isEmbedded());
        output.putFloat(EMBEDDED_ROLL_KEY, this.getEmbeddedRoll());
        output.putFloat(EMBEDDED_YAW_KEY, this.getEmbeddedYaw());
        output.putFloat(EMBEDDED_PITCH_KEY, this.getEmbeddedPitch());
        output.putDouble(EMBEDDED_X_KEY, this.embeddedPosition.x);
        output.putDouble(EMBEDDED_Y_KEY, this.embeddedPosition.y);
        output.putDouble(EMBEDDED_Z_KEY, this.embeddedPosition.z);
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.thrownStackCount = Math.max(1, input.getIntOr(STACK_COUNT_KEY, 1));
        this.entityData.set(DATA_EMBEDDED, input.getBooleanOr(EMBEDDED_KEY, false));
        this.entityData.set(DATA_EMBEDDED_ROLL, input.getFloatOr(EMBEDDED_ROLL_KEY, 0.0F));
        this.entityData.set(DATA_EMBEDDED_YAW, input.getFloatOr(EMBEDDED_YAW_KEY, this.getYRot()));
        this.entityData.set(DATA_EMBEDDED_PITCH, input.getFloatOr(EMBEDDED_PITCH_KEY, this.getXRot()));
        this.embeddedPosition = new Vec3(
            input.getDoubleOr(EMBEDDED_X_KEY, 0.0D),
            input.getDoubleOr(EMBEDDED_Y_KEY, 0.0D),
            input.getDoubleOr(EMBEDDED_Z_KEY, 0.0D)
        );

        if (this.isEmbedded()) {
            float embeddedYaw = this.getEmbeddedYaw();
            float embeddedPitch = this.getEmbeddedPitch();
            this.setPos(this.embeddedPosition);
            this.setYRot(embeddedYaw);
            this.setXRot(embeddedPitch);
            this.yRotO = embeddedYaw;
            this.xRotO = embeddedPitch;
        }
    }

    @Override
    public void playerTouch(Player player) {
        super.playerTouch(player);

        if (!(this.level() instanceof ServerLevel serverLevel) || !player.isAlive()) {
            return;
        }

        if (!this.isEmbedded()) {
            return;
        }

        tryPickupThrownWeapon(serverLevel, player);
    }

    public boolean usesPointFirstFlight() {
        return isPointFirstFlightWeapon(this.getItem());
    }

    private static boolean isPointFirstFlightWeapon(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        Item item = stack.getItem();
        if (item instanceof TridentItem || item == Items.TRIDENT) {
            return true;
        }

        String itemPath = BuiltInRegistries.ITEM.getKey(item).getPath();
        return itemPath.contains("spear") || itemPath.contains("javelin");
    }

    private void recordTrailPoint() {
        this.trailPoints.addLast(this.position());
        while (this.trailPoints.size() > MAX_TRAIL_POINTS) {
            this.trailPoints.removeFirst();
        }
    }

    private boolean canEmbedInBlock() {
        ItemStack stack = this.getItem();
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || isPointFirstFlightWeapon(stack);
    }

    private boolean shouldEmbedInBlock(Vec3 velocity, Vec3 normal) {
        if (this.usesPointFirstFlight()) {
            return true;
        }

        double speedSquared = velocity.lengthSqr();
        if (speedSquared < EMBED_MIN_SPEED_SQUARED) {
            return false;
        }

        Vec3 direction = velocity.normalize();
        double impactAlignment = -direction.dot(normal);
        if (impactAlignment < EMBED_HEAD_ON_THRESHOLD) {
            return false;
        }

        float impactRoll = this.getImpactRollDegrees();
        return Math.abs((float)Math.cos(Math.toRadians(impactRoll))) >= EMBEDDED_ROLL_THRESHOLD;
    }

    private void embedInBlock(BlockHitResult hitResult, Vec3 velocity, Vec3 normal) {
        Vec3 direction = velocity.lengthSqr() > 1.0E-5D ? velocity.normalize() : normal.scale(-1.0D);
        double horizontalSpeed = Math.sqrt(direction.x * direction.x + direction.z * direction.z);

        this.embeddedPosition = hitResult.getLocation()
            .add(direction.scale(EMBED_DEPTH))
            .subtract(normal.scale(EMBED_SURFACE_INSET));
        float embeddedRoll = this.usesPointFirstFlight()
            ? 0.0F
            : (Math.cos(Math.toRadians(this.getImpactRollDegrees())) >= 0.0D ? 0.0F : 180.0F);
        float embeddedYaw = (float)Math.toDegrees(Math.atan2(direction.x, direction.z));
        float embeddedPitch = (float)Math.toDegrees(Math.atan2(direction.y, horizontalSpeed));
        this.entityData.set(DATA_EMBEDDED, true);
        this.entityData.set(DATA_EMBEDDED_ROLL, embeddedRoll);
        this.entityData.set(DATA_EMBEDDED_YAW, embeddedYaw);
        this.entityData.set(DATA_EMBEDDED_PITCH, embeddedPitch);

        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(this.embeddedPosition);
        this.setYRot(embeddedYaw);
        this.setXRot(embeddedPitch);
        this.yRotO = embeddedYaw;
        this.xRotO = embeddedPitch;
        this.trailPoints.clear();
        this.trailPoints.addLast(this.embeddedPosition);

        if (this.level() instanceof ServerLevel serverLevel) {
            this.playEmbedSound(serverLevel, hitResult);
        }
    }

    private float getImpactRollDegrees() {
        return this.tickCount * FLIGHT_SPIN_SPEED + this.getSpinPhaseOffsetDegrees();
    }

    private float getThrownDamage(ServerLevel serverLevel, ItemStack thrownStack, net.minecraft.world.entity.Entity target, DamageSource source) {
        float itemDamage = getThrownBaseDamage(thrownStack);
        float speed = (float)this.getDeltaMovement().length();
        float velocityMultiplier = VELOCITY_DAMAGE_BASE + speed * VELOCITY_DAMAGE_FACTOR;
        float damage = itemDamage * velocityMultiplier;

        if (!this.hitBlock) {
            damage *= CLEAN_FLIGHT_DAMAGE_BONUS;
        }

        return EnchantmentHelper.modifyDamage(serverLevel, thrownStack, target, source, damage);
    }

    private static float getThrownBaseDamage(ItemStack thrownStack) {
        float attackDamage = getMainHandAttackDamage(thrownStack);

        if (isPointFirstFlightWeapon(thrownStack)) {
            float swordBaseline = Math.max(attackDamage, 4.0F);
            return Math.max(swordBaseline * SPEAR_DAMAGE_MULTIPLIER, swordBaseline + SPEAR_DAMAGE_FLAT_BONUS);
        }

        if (thrownStack.is(ItemTags.SWORDS)) {
            return attackDamage;
        }

        if (thrownStack.is(ItemTags.AXES)) {
            return Math.max(3.5F, attackDamage * 0.75F);
        }

        if (thrownStack.is(ItemTags.PICKAXES)) {
            return Math.max(2.0F, attackDamage * 0.6F);
        }

        if (thrownStack.is(ItemTags.SHOVELS) || thrownStack.is(ItemTags.HOES)) {
            return Math.max(1.25F, attackDamage * 0.5F);
        }

        if (thrownStack.getItem() instanceof BlockItem) {
            return BLOCK_BASE_DAMAGE;
        }

        if (thrownStack.isDamageableItem()) {
            return Math.max(1.0F, attackDamage * 0.4F);
        }

        return MISC_BASE_DAMAGE;
    }

    private static float getMainHandAttackDamage(ItemStack thrownStack) {
        final float[] additive = new float[] {BASE_HAND_DAMAGE};
        final float[] multipliedBase = new float[] {0.0F};
        final float[] multipliedTotal = new float[] {1.0F};

        ItemAttributeModifiers modifiers = thrownStack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        modifiers.forEach(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.is(Attributes.ATTACK_DAMAGE)) {
                accumulateAttackDamageModifier(modifier, additive, multipliedBase, multipliedTotal);
            }
        });
        EnchantmentHelper.forEachModifier(thrownStack, EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.is(Attributes.ATTACK_DAMAGE)) {
                accumulateAttackDamageModifier(modifier, additive, multipliedBase, multipliedTotal);
            }
        });

        return (additive[0] + BASE_HAND_DAMAGE * multipliedBase[0]) * multipliedTotal[0];
    }

    private void applyThrownHitEffects(ServerLevel serverLevel, ItemStack thrownStack, EntityHitResult hitResult, DamageSource source) {
        if (!(this.getOwner() instanceof LivingEntity attacker) || !(hitResult.getEntity() instanceof LivingEntity target)) {
            return;
        }

        attacker.setLastHurtMob(target);

        HolderLookup.RegistryLookup<Enchantment> enchantments = serverLevel.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int fireAspectLevel = thrownStack.getEnchantmentLevel(enchantments.getOrThrow(Enchantments.FIRE_ASPECT));
        if (fireAspectLevel > 0) {
            target.igniteForSeconds(fireAspectLevel * 4.0F);
        }

        EnchantmentHelper.doPostAttackEffectsWithItemSource(serverLevel, target, source, thrownStack);
        if (attacker instanceof Player player) {
            thrownStack.hurtEnemy(target, player);
        }
    }

    private void playBounceSound(ServerLevel serverLevel, BlockHitResult hitResult, double speedSquared) {
        BlockState blockState = serverLevel.getBlockState(hitResult.getBlockPos());
        SoundType soundType = blockState.getSoundType();
        float speedFactor = (float)Math.min(1.0D, Math.sqrt(speedSquared));

        serverLevel.playSound(
            null,
            hitResult.getLocation().x,
            hitResult.getLocation().y,
            hitResult.getLocation().z,
            soundType.getHitSound(),
            SoundSource.BLOCKS,
            0.55F + speedFactor * 0.35F,
            0.88F + serverLevel.random.nextFloat() * 0.14F
        );

        serverLevel.playSound(
            null,
            hitResult.getLocation().x,
            hitResult.getLocation().y,
            hitResult.getLocation().z,
            SoundEvents.SHIELD_BLOCK,
            SoundSource.PLAYERS,
            0.18F + speedFactor * 0.18F,
            0.9F + serverLevel.random.nextFloat() * 0.12F
        );
    }

    private void playEmbedSound(ServerLevel serverLevel, BlockHitResult hitResult) {
        BlockState blockState = serverLevel.getBlockState(hitResult.getBlockPos());
        SoundType soundType = blockState.getSoundType();

        serverLevel.playSound(
            null,
            hitResult.getLocation().x,
            hitResult.getLocation().y,
            hitResult.getLocation().z,
            soundType.getPlaceSound(),
            SoundSource.BLOCKS,
            0.8F,
            0.72F + serverLevel.random.nextFloat() * 0.1F
        );

        if (this.usesPointFirstFlight()) {
            serverLevel.playSound(
                null,
                hitResult.getLocation().x,
                hitResult.getLocation().y,
                hitResult.getLocation().z,
                SoundEvents.TRIDENT_HIT_GROUND,
                SoundSource.PLAYERS,
                0.65F,
                0.9F + serverLevel.random.nextFloat() * 0.08F
            );
        } else {
            serverLevel.playSound(
                null,
                hitResult.getLocation().x,
                hitResult.getLocation().y,
                hitResult.getLocation().z,
                SoundEvents.SHIELD_BLOCK,
                SoundSource.PLAYERS,
                0.3F,
                0.7F + serverLevel.random.nextFloat() * 0.1F
            );
        }
    }

    private static void accumulateAttackDamageModifier(
        AttributeModifier modifier,
        float[] additive,
        float[] multipliedBase,
        float[] multipliedTotal
    ) {
        switch (modifier.operation()) {
            case ADD_VALUE -> additive[0] += (float)modifier.amount();
            case ADD_MULTIPLIED_BASE -> multipliedBase[0] += (float)modifier.amount();
            case ADD_MULTIPLIED_TOTAL -> multipliedTotal[0] *= 1.0F + (float)modifier.amount();
        }
    }

    private void tryPickupThrownWeapon(ServerLevel serverLevel, Player player) {
        ItemStack recoveredStack = this.getItem().copyWithCount(this.thrownStackCount);
        if (recoveredStack.isEmpty()) {
            this.discard();
            return;
        }

        if (!player.getInventory().add(recoveredStack)) {
            return;
        }

        player.take(this, this.thrownStackCount);
        serverLevel.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F, ((serverLevel.random.nextFloat() - serverLevel.random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
        this.discard();
    }

    private void dropAsItemAndDiscard() {
        if (this.dropped) {
            return;
        }
        this.dropped = true;

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            this.discard();
            return;
        }

        ItemStack stack = this.getItem().copyWithCount(this.thrownStackCount);
        if (!stack.isEmpty()) {
            ItemEntity itemEntity = new ItemEntity(serverLevel, this.getX(), this.getY(), this.getZ(), stack);
            itemEntity.setDeltaMovement(this.getDeltaMovement().scale(0.2D));
            itemEntity.setPickUpDelay(5);
            serverLevel.addFreshEntity(itemEntity);
        }
        this.discard();
    }
}
