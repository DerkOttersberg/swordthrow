package com.derko.swordthrow.entity;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
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
    private static final int MAX_TRAIL_POINTS = 20;
    private static final float BASE_HAND_DAMAGE = 1.0F;
    private static final float VELOCITY_DAMAGE_FACTOR = 0.7F;
    private static final float VELOCITY_DAMAGE_BASE = 0.65F;
    private static final float CLEAN_FLIGHT_DAMAGE_BONUS = 1.35F;
    private boolean dropped;
    private boolean hitBlock;
    private final Deque<Vec3d> trailPoints = new ArrayDeque<>();

    public ThrownSwordEntity(EntityType<? extends ThrownSwordEntity> entityType, World world) {
        super(entityType, world);
    }

    public ThrownSwordEntity(World world, LivingEntity owner, ItemStack sword) {
        super(ModEntities.THROWN_SWORD, owner, world, sword.copyWithCount(1));
    }

    @Override
    public void tick() {
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
        super.onEntityHit(hitResult);

        if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        ItemStack swordStack = this.getStack();
        DamageSource source = this.getDamageSources().thrown(this, this.getOwner());
        float damage = this.getThrownDamage(serverWorld, swordStack, hitResult.getEntity(), source);
        boolean dealtDamage = hitResult.getEntity().damage(serverWorld, source, damage);

        if (dealtDamage) {
            this.applySwordHitEffects(serverWorld, swordStack, hitResult);
        }

        this.dropAsItemAndDiscard();
    }

    @Override
    protected void onBlockHit(BlockHitResult hitResult) {
        super.onBlockHit(hitResult);

        if (this.getEntityWorld().isClient()) {
            return;
        }

        this.hitBlock = true;

        Vec3d velocity = this.getVelocity();
        Direction side = hitResult.getSide();
        Vec3d normal = Vec3d.of(side.getVector());
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

    public List<Vec3d> getTrailPoints() {
        if (this.trailPoints.isEmpty()) {
            return Collections.emptyList();
        }
        return List.copyOf(this.trailPoints);
    }

    private void recordTrailPoint() {
        this.trailPoints.addLast(new Vec3d(this.getX(), this.getY(), this.getZ()));
        while (this.trailPoints.size() > MAX_TRAIL_POINTS) {
            this.trailPoints.removeFirst();
        }
    }

    private float getThrownDamage(ServerWorld serverWorld, ItemStack swordStack, net.minecraft.entity.Entity target, DamageSource source) {
        float swordDamage = getSwordAttackDamage(swordStack);
        float speed = (float)this.getVelocity().length();
        float velocityMultiplier = VELOCITY_DAMAGE_BASE + speed * VELOCITY_DAMAGE_FACTOR;
        float damage = swordDamage * velocityMultiplier;

        if (!this.hitBlock) {
            damage *= CLEAN_FLIGHT_DAMAGE_BONUS;
        }

        return EnchantmentHelper.getDamage(serverWorld, swordStack, target, source, damage);
    }

    private static float getSwordAttackDamage(ItemStack swordStack) {
        final float[] additive = new float[] {BASE_HAND_DAMAGE};
        final float[] multipliedBase = new float[] {0.0F};
        final float[] multipliedTotal = new float[] {1.0F};

        swordStack.applyAttributeModifiers(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
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

    private void applySwordHitEffects(ServerWorld serverWorld, ItemStack swordStack, EntityHitResult hitResult) {
        if (!(this.getOwner() instanceof LivingEntity attacker) || !(hitResult.getEntity() instanceof LivingEntity target)) {
            return;
        }

        attacker.onAttacking(target);

        int fireAspectLevel = EnchantmentHelper.getLevel(
            serverWorld.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getEntry(
                serverWorld.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getValueOrThrow(Enchantments.FIRE_ASPECT)
            ),
            swordStack
        );
        if (fireAspectLevel > 0) {
            target.setOnFireForTicks(fireAspectLevel * 80);
        }

        EnchantmentHelper.onTargetDamaged(serverWorld, target, this.getDamageSources().thrown(this, attacker), swordStack);
        swordStack.postHit(target, attacker);
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

        ItemStack stack = this.getStack().copyWithCount(1);
        if (!stack.isEmpty()) {
            this.dropStack(serverWorld, stack, 0.1F);
        }
        this.discard();
    }
}
