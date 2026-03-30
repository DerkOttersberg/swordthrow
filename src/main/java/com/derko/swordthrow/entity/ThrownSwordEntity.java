package com.derko.swordthrow.entity;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.TridentItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class ThrownSwordEntity extends ThrownItemEntity {
    private static final int BOUNCE_GRACE_TICKS = 2;
    private static final double BOUNCE_DAMPING = 0.58D;
    private static final double WALL_HORIZONTAL_DAMPING = 0.72D;
    private static final double WALL_VERTICAL_DAMPING = 0.86D;
    private static final double MIN_BOUNCE_SPEED_SQUARED = 0.18D * 0.18D;
    private static final double BOUNCE_SURFACE_OFFSET = 0.08D;
    private static final double EMBED_DEPTH = 0.012D;
    private static final double EMBED_MIN_SPEED_SQUARED = 0.42D * 0.42D;
    private static final double EMBED_HEAD_ON_THRESHOLD = 0.32D;
    private static final float EMBEDDED_ROLL_THRESHOLD = 0.38F;
    private static final int MAX_TRAIL_POINTS = 20;
    private static final float BASE_HAND_DAMAGE = 1.0F;
    private static final float VELOCITY_DAMAGE_FACTOR = 0.7F;
    private static final float VELOCITY_DAMAGE_BASE = 0.65F;
    private static final float CLEAN_FLIGHT_DAMAGE_BONUS = 1.35F;
    private static final float SPEAR_DAMAGE_MULTIPLIER = 1.45F;
    private static final float SPEAR_DAMAGE_FLAT_BONUS = 2.5F;
    private static final float BLOCK_BASE_DAMAGE = 0.35F;
    private static final float MISC_BASE_DAMAGE = 0.6F;
    private static final float FLIGHT_SPIN_SPEED = 34.0F;
    private static final String STACK_COUNT_KEY = "ThrownStackCount";
    private static final String EMBED_YAW_KEY = "EmbeddedYaw";
    private static final String EMBED_PITCH_KEY = "EmbeddedPitch";

    private boolean dropped;
    private boolean hitBlock;
    private boolean embedded;
    private int bounceGraceTicks;
    private int thrownStackCount = 1;
    private float embeddedRoll;
    private float embeddedYaw;
    private float embeddedPitch;
    private Vec3d embeddedPosition = Vec3d.ZERO;
    private final Deque<Vec3d> trailPoints = new ArrayDeque<>();

    public ThrownSwordEntity(EntityType<? extends ThrownSwordEntity> entityType, World world) {
        super(entityType, world);
    }

    public ThrownSwordEntity(World world, LivingEntity owner, ItemStack stack) {
        super(ModEntities.THROWN_SWORD, owner, world);
        this.setItem(stack.copy());
        this.thrownStackCount = Math.max(1, stack.getCount());
    }

    @Override
    public void tick() {
        if (this.bounceGraceTicks > 0) {
            this.bounceGraceTicks--;
        }

        if (this.embedded) {
            this.age++;
            this.setNoGravity(true);
            this.setVelocity(Vec3d.ZERO);
            this.setPosition(this.embeddedPosition);
            this.setYaw(this.embeddedYaw);
            this.setPitch(this.embeddedPitch);
            this.prevYaw = this.embeddedYaw;
            this.prevPitch = this.embeddedPitch;

            if (this.getEntityWorld().isClient()) {
                this.trailPoints.clear();
                this.trailPoints.addLast(this.embeddedPosition);
            }
            return;
        }

        super.tick();

        if (this.getEntityWorld().isClient()) {
            this.recordTrailPoint();
            return;
        }

        this.setVelocity(this.getVelocity().multiply(0.992D, 0.985D, 0.992D));

        if (this.age > 120) {
            this.dropAsItemAndDiscard();
        }
    }

    @Override
    protected double getGravity() {
        return 0.045D;
    }

    @Override
    protected void onEntityHit(EntityHitResult hitResult) {
        if (this.embedded) {
            return;
        }

        super.onEntityHit(hitResult);

        if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        ItemStack thrownStack = this.getStack();
        DamageSource source = this.getDamageSources().thrown(this, this.getOwner());
        float damage = this.getThrownDamage(serverWorld, thrownStack, hitResult.getEntity(), source);
        boolean dealtDamage = hitResult.getEntity().damage(source, damage);

        if (dealtDamage) {
            this.applyThrownHitEffects(serverWorld, thrownStack, hitResult);
        }

        this.dropAsItemAndDiscard();
    }

    @Override
    protected void onBlockHit(BlockHitResult hitResult) {
        if (this.embedded || this.bounceGraceTicks > 0) {
            return;
        }

        super.onBlockHit(hitResult);

        Vec3d velocity = this.getVelocity();
        Direction side = hitResult.getSide();
        Vec3d normal = Vec3d.of(side.getVector());
        boolean firstBlockHit = !this.hitBlock;
        this.hitBlock = true;

        if (firstBlockHit && this.canEmbedInBlock() && this.shouldEmbedInBlock(velocity, normal)) {
            this.embedInBlock(hitResult, velocity, normal);
            return;
        }

        if (this.getEntityWorld().isClient()) {
            return;
        }

        double normalComponent = velocity.dotProduct(normal);
        Vec3d normalVelocity = normal.multiply(normalComponent);
        Vec3d tangentialVelocity = velocity.subtract(normalVelocity).multiply(BOUNCE_DAMPING);
        Vec3d reflectedVelocity = tangentialVelocity.subtract(normalVelocity.multiply(0.65D));

        if (side.getAxis().isHorizontal()) {
            reflectedVelocity = new Vec3d(
                reflectedVelocity.x * WALL_HORIZONTAL_DAMPING,
                reflectedVelocity.y * WALL_VERTICAL_DAMPING,
                reflectedVelocity.z * WALL_HORIZONTAL_DAMPING
            );
        }

        if (reflectedVelocity.lengthSquared() < MIN_BOUNCE_SPEED_SQUARED) {
            this.dropAsItemAndDiscard();
            return;
        }

        Vec3d bouncePosition = hitResult.getPos()
            .add(normal.multiply(BOUNCE_SURFACE_OFFSET + 0.04D))
            .add(reflectedVelocity.normalize().multiply(0.02D));
        this.setPosition(bouncePosition);
        this.setVelocity(reflectedVelocity);
        this.bounceGraceTicks = BOUNCE_GRACE_TICKS;
        this.playBounceSound((ServerWorld) this.getEntityWorld(), hitResult, reflectedVelocity.lengthSquared());
    }

    @Override
    protected Item getDefaultItem() {
        return Items.IRON_SWORD;
    }

    @Override
    public void onPlayerCollision(PlayerEntity player) {
        super.onPlayerCollision(player);

        if (!this.embedded || !(this.getEntityWorld() instanceof ServerWorld serverWorld) || !player.isAlive()) {
            return;
        }

        this.tryPickupEmbeddedWeapon(serverWorld, player);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt(STACK_COUNT_KEY, this.thrownStackCount);
        nbt.putFloat(EMBED_YAW_KEY, this.embeddedYaw);
        nbt.putFloat(EMBED_PITCH_KEY, this.embeddedPitch);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.thrownStackCount = Math.max(1, nbt.contains(STACK_COUNT_KEY) ? nbt.getInt(STACK_COUNT_KEY) : 1);
        this.embeddedYaw = nbt.contains(EMBED_YAW_KEY) ? nbt.getFloat(EMBED_YAW_KEY) : this.getYaw();
        this.embeddedPitch = nbt.contains(EMBED_PITCH_KEY) ? nbt.getFloat(EMBED_PITCH_KEY) : this.getPitch();
    }

    public List<Vec3d> getTrailPoints() {
        if (this.trailPoints.isEmpty()) {
            return Collections.emptyList();
        }
        return List.copyOf(this.trailPoints);
    }

    public boolean isEmbedded() {
        return this.embedded;
    }

    public float getEmbeddedRoll() {
        return this.embeddedRoll;
    }

    public float getSpinPhaseOffsetDegrees() {
        return (this.getId() * 57.0F) % 360.0F;
    }

    public boolean usesPointFirstFlight() {
        return isPointFirstFlightWeapon(this.getStack());
    }

    private void recordTrailPoint() {
        this.trailPoints.addLast(new Vec3d(this.getX(), this.getY(), this.getZ()));
        while (this.trailPoints.size() > MAX_TRAIL_POINTS) {
            this.trailPoints.removeFirst();
        }
    }

    private static boolean isPointFirstFlightWeapon(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        Item item = stack.getItem();
        if (item instanceof TridentItem || item == Items.TRIDENT) {
            return true;
        }

        String itemPath = Registries.ITEM.getId(item).getPath();
        return itemPath.contains("spear") || itemPath.contains("javelin");
    }

    private boolean canEmbedInBlock() {
        ItemStack stack = this.getStack();
        return stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.AXES) || isPointFirstFlightWeapon(stack);
    }

    private boolean shouldEmbedInBlock(Vec3d velocity, Vec3d normal) {
        if (this.usesPointFirstFlight()) {
            return true;
        }

        double speedSquared = velocity.lengthSquared();
        if (speedSquared < EMBED_MIN_SPEED_SQUARED) {
            return false;
        }

        Vec3d direction = velocity.normalize();
        double impactAlignment = -direction.dotProduct(normal);
        if (impactAlignment < EMBED_HEAD_ON_THRESHOLD) {
            return false;
        }

        float impactRoll = this.getImpactRollDegrees();
        return Math.abs((float) Math.cos(Math.toRadians(impactRoll))) >= EMBEDDED_ROLL_THRESHOLD;
    }

    private void embedInBlock(BlockHitResult hitResult, Vec3d velocity, Vec3d normal) {
        Vec3d direction = velocity.lengthSquared() > 1.0E-5D ? velocity.normalize() : normal.multiply(-1.0D);
        Vec3d surfaceFacing = normal.multiply(-1.0D);
        double alignment = Math.max(0.0D, -direction.dotProduct(normal));
        Vec3d embedDirection = direction.multiply(0.55D + alignment * 0.45D)
            .add(surfaceFacing.multiply(1.0D - alignment * 0.45D));
        if (embedDirection.lengthSquared() < 1.0E-5D) {
            embedDirection = surfaceFacing;
        } else {
            embedDirection = embedDirection.normalize();
        }
        double horizontalSpeed = Math.sqrt(embedDirection.x * embedDirection.x + embedDirection.z * embedDirection.z);

        this.embedded = true;
        this.bounceGraceTicks = 0;
        this.embeddedPosition = hitResult.getPos().subtract(normal.multiply(EMBED_DEPTH));
        this.embeddedRoll = normalizeRoll(this.getImpactRollDegrees());

        this.embeddedYaw = (float) Math.toDegrees(Math.atan2(embedDirection.x, embedDirection.z));
        this.embeddedPitch = (float) Math.toDegrees(Math.atan2(embedDirection.y, horizontalSpeed));

        this.setNoGravity(true);
        this.setVelocity(Vec3d.ZERO);
        this.setPosition(this.embeddedPosition);
        this.setYaw(this.embeddedYaw);
        this.setPitch(this.embeddedPitch);
        this.prevYaw = this.embeddedYaw;
        this.prevPitch = this.embeddedPitch;
        this.trailPoints.clear();
        this.trailPoints.addLast(this.embeddedPosition);

        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            this.playEmbedSound(serverWorld, hitResult);
        }
    }

    private float getImpactRollDegrees() {
        return this.age * FLIGHT_SPIN_SPEED + this.getSpinPhaseOffsetDegrees();
    }

    private static float normalizeRoll(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped < 0.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    private float getThrownDamage(ServerWorld serverWorld, ItemStack thrownStack, net.minecraft.entity.Entity target, DamageSource source) {
        float itemDamage = getThrownBaseDamage(thrownStack);
        float speed = (float) this.getVelocity().length();
        float velocityMultiplier = VELOCITY_DAMAGE_BASE + speed * VELOCITY_DAMAGE_FACTOR;
        float damage = itemDamage * velocityMultiplier;

        if (!this.hitBlock) {
            damage *= CLEAN_FLIGHT_DAMAGE_BONUS;
        }

        return EnchantmentHelper.getDamage(serverWorld, thrownStack, target, source, damage);
    }

    private static float getThrownBaseDamage(ItemStack thrownStack) {
        float attackDamage = getMainHandAttackDamage(thrownStack);

        if (isPointFirstFlightWeapon(thrownStack)) {
            float swordBaseline = Math.max(attackDamage, 4.0F);
            return Math.max(swordBaseline * SPEAR_DAMAGE_MULTIPLIER, swordBaseline + SPEAR_DAMAGE_FLAT_BONUS);
        }

        if (thrownStack.isIn(ItemTags.SWORDS)) {
            return attackDamage;
        }

        if (thrownStack.isIn(ItemTags.AXES)) {
            return Math.max(3.5F, attackDamage * 0.75F);
        }

        if (thrownStack.isIn(ItemTags.PICKAXES)) {
            return Math.max(2.0F, attackDamage * 0.6F);
        }

        if (thrownStack.isIn(ItemTags.SHOVELS) || thrownStack.isIn(ItemTags.HOES)) {
            return Math.max(1.25F, attackDamage * 0.5F);
        }

        if (thrownStack.getItem() instanceof BlockItem) {
            return BLOCK_BASE_DAMAGE;
        }

        if (thrownStack.isDamageable()) {
            return Math.max(1.0F, attackDamage * 0.4F);
        }

        return MISC_BASE_DAMAGE;
    }

    private static float getMainHandAttackDamage(ItemStack thrownStack) {
        final float[] additive = new float[] {BASE_HAND_DAMAGE};
        final float[] multipliedBase = new float[] {0.0F};
        final float[] multipliedTotal = new float[] {1.0F};

        thrownStack.applyAttributeModifiers(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (!attribute.equals(EntityAttributes.GENERIC_ATTACK_DAMAGE)) {
                return;
            }

            if (modifier.operation() == EntityAttributeModifier.Operation.ADD_VALUE) {
                additive[0] += (float) modifier.value();
            } else if (modifier.operation() == EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                multipliedBase[0] += (float) modifier.value();
            } else if (modifier.operation() == EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                multipliedTotal[0] *= 1.0F + (float) modifier.value();
            }
        });

        return (additive[0] + BASE_HAND_DAMAGE * multipliedBase[0]) * multipliedTotal[0];
    }

    private void applyThrownHitEffects(ServerWorld serverWorld, ItemStack thrownStack, EntityHitResult hitResult) {
        if (!(this.getOwner() instanceof LivingEntity attacker) || !(hitResult.getEntity() instanceof LivingEntity target)) {
            return;
        }

        attacker.onAttacking(target);

        int fireAspectLevel = EnchantmentHelper.getLevel(
            serverWorld.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(Enchantments.FIRE_ASPECT),
            thrownStack
        );
        if (fireAspectLevel > 0) {
            target.setOnFireForTicks(fireAspectLevel * 80);
        }

        EnchantmentHelper.onTargetDamaged(serverWorld, target, this.getDamageSources().thrown(this, attacker), thrownStack);
        if (attacker instanceof PlayerEntity playerAttacker) {
            thrownStack.postHit(target, playerAttacker);
            thrownStack.postDamageEntity(target, playerAttacker);
        }
    }

    private void tryPickupEmbeddedWeapon(ServerWorld serverWorld, PlayerEntity player) {
        ItemStack recoveredStack = this.getStack().copyWithCount(this.thrownStackCount);
        if (recoveredStack.isEmpty()) {
            this.discard();
            return;
        }

        if (!player.getInventory().insertStack(recoveredStack)) {
            return;
        }

        player.sendPickup(this, this.thrownStackCount);
        serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sound.SoundEvents.ENTITY_ITEM_PICKUP, player.getSoundCategory(), 0.2F, ((serverWorld.random.nextFloat() - serverWorld.random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
        this.discard();
    }

    private void playBounceSound(ServerWorld serverWorld, BlockHitResult hitResult, double speedSquared) {
        BlockState blockState = serverWorld.getBlockState(hitResult.getBlockPos());
        BlockSoundGroup soundGroup = blockState.getSoundGroup();
        float speedFactor = (float) Math.min(1.0D, Math.sqrt(speedSquared));

        serverWorld.playSound(
            null,
            hitResult.getPos().x,
            hitResult.getPos().y,
            hitResult.getPos().z,
            soundGroup.getHitSound(),
            SoundCategory.BLOCKS,
            0.55F + speedFactor * 0.35F,
            0.88F + serverWorld.random.nextFloat() * 0.14F
        );

        serverWorld.playSound(
            null,
            hitResult.getPos().x,
            hitResult.getPos().y,
            hitResult.getPos().z,
            SoundEvents.ITEM_SHIELD_BLOCK,
            SoundCategory.PLAYERS,
            0.18F + speedFactor * 0.18F,
            0.9F + serverWorld.random.nextFloat() * 0.12F
        );
    }

    private void playEmbedSound(ServerWorld serverWorld, BlockHitResult hitResult) {
        BlockState blockState = serverWorld.getBlockState(hitResult.getBlockPos());
        BlockSoundGroup soundGroup = blockState.getSoundGroup();

        serverWorld.playSound(
            null,
            hitResult.getPos().x,
            hitResult.getPos().y,
            hitResult.getPos().z,
            soundGroup.getPlaceSound(),
            SoundCategory.BLOCKS,
            0.8F,
            0.72F + serverWorld.random.nextFloat() * 0.1F
        );

        if (this.usesPointFirstFlight()) {
            serverWorld.playSound(
                null,
                hitResult.getPos().x,
                hitResult.getPos().y,
                hitResult.getPos().z,
                SoundEvents.ITEM_TRIDENT_HIT_GROUND,
                SoundCategory.PLAYERS,
                0.65F,
                0.9F + serverWorld.random.nextFloat() * 0.08F
            );
        } else {
            serverWorld.playSound(
                null,
                hitResult.getPos().x,
                hitResult.getPos().y,
                hitResult.getPos().z,
                SoundEvents.ITEM_SHIELD_BLOCK,
                SoundCategory.PLAYERS,
                0.3F,
                0.7F + serverWorld.random.nextFloat() * 0.1F
            );
        }
    }

    private void dropAsItemAndDiscard() {
        if (this.dropped) {
            return;
        }
        this.dropped = true;

        if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
            this.discard();
            return;
        }

        ItemStack stack = this.getStack().copyWithCount(this.thrownStackCount);
        if (!stack.isEmpty()) {
            ItemEntity itemEntity = new ItemEntity(serverWorld, this.getX(), this.getY(), this.getZ(), stack);
            itemEntity.setVelocity(this.getVelocity().multiply(0.2D));
            itemEntity.setPickupDelay(5);
            serverWorld.spawnEntity(itemEntity);
        }
        this.discard();
    }
}
