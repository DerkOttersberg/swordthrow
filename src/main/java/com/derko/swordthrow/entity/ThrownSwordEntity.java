package com.derko.swordthrow.entity;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class ThrownSwordEntity extends ThrownItemEntity {
    private static final double BOUNCE_DAMPING = 0.68D;
    private static final double WALL_HORIZONTAL_DAMPING = 0.42D;
    private static final double WALL_VERTICAL_DAMPING = 0.92D;
    private static final double MIN_BOUNCE_SPEED_SQUARED = 0.18D * 0.18D;
    private static final double BOUNCE_SURFACE_OFFSET = 0.08D;
    private static final double EMBED_DEPTH = 0.012D;
    private static final double EMBED_MIN_SPEED_SQUARED = 0.42D * 0.42D;
    private static final double EMBED_HEAD_ON_THRESHOLD = 0.24D;
    private static final float EMBEDDED_ROLL_THRESHOLD = 0.38F;
    private static final int MAX_TRAIL_POINTS = 20;
    private static final float BASE_HAND_DAMAGE = 1.0F;
    private static final float VELOCITY_DAMAGE_FACTOR = 0.7F;
    private static final float VELOCITY_DAMAGE_BASE = 0.65F;
    private static final float CLEAN_FLIGHT_DAMAGE_BONUS = 1.35F;
    private static final float BLOCK_BASE_DAMAGE = 0.35F;
    private static final float MISC_BASE_DAMAGE = 0.6F;
    private static final float FLIGHT_SPIN_SPEED = 34.0F;
    private static final String STACK_COUNT_KEY = "ThrownStackCount";
    private boolean dropped;
    private boolean hitBlock;
    private boolean embedded;
    private int thrownStackCount = 1;
    private float embeddedRoll;
    private Vec3d embeddedPosition = Vec3d.ZERO;
    private final Deque<Vec3d> trailPoints = new ArrayDeque<>();

    public ThrownSwordEntity(EntityType<? extends ThrownSwordEntity> entityType, World world) {
        super(entityType, world);
    }

    public ThrownSwordEntity(World world, LivingEntity owner, ItemStack stack) {
        super(ModEntities.THROWN_SWORD, owner, world, stack.copy());
        this.thrownStackCount = Math.max(1, stack.getCount());
    }

    @Override
    public void tick() {
        if (this.embedded) {
            this.age++;
            this.setNoGravity(true);
            this.setVelocity(Vec3d.ZERO);
            this.setPosition(this.embeddedPosition);

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

        this.setVelocity(this.getVelocity().multiply(0.985D, 0.97D, 0.985D));

        if (this.age > 120) {
            this.dropAsItemAndDiscard();
        }
    }

    @Override
    protected double getGravity() {
        return 0.06D;
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
        boolean dealtDamage = hitResult.getEntity().damage(serverWorld, source, damage);

        if (dealtDamage) {
            this.applyThrownHitEffects(serverWorld, thrownStack, hitResult);
        }

        this.dropAsItemAndDiscard();
    }

    @Override
    protected void onBlockHit(BlockHitResult hitResult) {
        if (this.embedded) {
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
        Vec3d reflectedVelocity = velocity.subtract(normal.multiply(2.0D * normalComponent)).multiply(BOUNCE_DAMPING);

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

        Vec3d bouncePosition = hitResult.getPos().add(normal.multiply(BOUNCE_SURFACE_OFFSET));
        this.setPosition(bouncePosition);
        this.setVelocity(reflectedVelocity);
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
    protected void writeCustomData(WriteView writeView) {
        super.writeCustomData(writeView);
        writeView.putInt(STACK_COUNT_KEY, this.thrownStackCount);
    }

    @Override
    protected void readCustomData(ReadView readView) {
        super.readCustomData(readView);
        this.thrownStackCount = Math.max(1, readView.getInt(STACK_COUNT_KEY, 1));
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
        return Math.abs((float)Math.cos(Math.toRadians(impactRoll))) >= EMBEDDED_ROLL_THRESHOLD;
    }

    private void embedInBlock(BlockHitResult hitResult, Vec3d velocity, Vec3d normal) {
        Vec3d direction = velocity.lengthSquared() > 1.0E-5D ? velocity.normalize() : normal;
        double horizontalSpeed = Math.sqrt(direction.x * direction.x + direction.z * direction.z);

        this.embedded = true;
        this.embeddedPosition = hitResult.getPos().subtract(normal.multiply(EMBED_DEPTH));
        this.embeddedRoll = Math.cos(Math.toRadians(this.getImpactRollDegrees())) >= 0.0D ? 0.0F : 180.0F;

        this.setNoGravity(true);
        this.setVelocity(Vec3d.ZERO);
        this.setPosition(this.embeddedPosition);
        this.setYaw((float)Math.toDegrees(Math.atan2(direction.x, direction.z)));
        this.setPitch((float)Math.toDegrees(Math.atan2(direction.y, horizontalSpeed)));
        this.trailPoints.clear();
        this.trailPoints.addLast(this.embeddedPosition);
    }

    private float getImpactRollDegrees() {
        return this.age * FLIGHT_SPIN_SPEED + this.getSpinPhaseOffsetDegrees();
    }

    private float getThrownDamage(ServerWorld serverWorld, ItemStack thrownStack, net.minecraft.entity.Entity target, DamageSource source) {
        float itemDamage = getThrownBaseDamage(thrownStack);
        float speed = (float)this.getVelocity().length();
        float velocityMultiplier = VELOCITY_DAMAGE_BASE + speed * VELOCITY_DAMAGE_FACTOR;
        float damage = itemDamage * velocityMultiplier;

        if (!this.hitBlock) {
            damage *= CLEAN_FLIGHT_DAMAGE_BONUS;
        }

        return EnchantmentHelper.getDamage(serverWorld, thrownStack, target, source, damage);
    }

    private static float getThrownBaseDamage(ItemStack thrownStack) {
        float attackDamage = getMainHandAttackDamage(thrownStack);

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
            if (!attribute.equals(EntityAttributes.ATTACK_DAMAGE)) {
                return;
            }

            if (modifier.operation() == EntityAttributeModifier.Operation.ADD_VALUE) {
                additive[0] += (float)modifier.value();
            } else if (modifier.operation() == EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                multipliedBase[0] += (float)modifier.value();
            } else if (modifier.operation() == EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                multipliedTotal[0] *= 1.0F + (float)modifier.value();
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
            serverWorld.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getEntry(
                serverWorld.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getValueOrThrow(Enchantments.FIRE_ASPECT)
            ),
            thrownStack
        );
        if (fireAspectLevel > 0) {
            target.setOnFireForTicks(fireAspectLevel * 80);
        }

        EnchantmentHelper.onTargetDamaged(serverWorld, target, this.getDamageSources().thrown(this, attacker), thrownStack);
        thrownStack.postHit(target, attacker);
        thrownStack.postDamageEntity(target, attacker);
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
