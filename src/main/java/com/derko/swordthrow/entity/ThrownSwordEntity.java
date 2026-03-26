package com.derko.swordthrow.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
    private boolean dropped;

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

        DamageSource source = this.getDamageSources().thrown(this, this.getOwner());
        float speedBonus = (float) (this.getVelocity().length() * 3.5D);
        hitResult.getEntity().damage(serverWorld, source, 6.0F + speedBonus);
        this.dropAsItemAndDiscard();
    }

    @Override
    protected void onBlockHit(BlockHitResult hitResult) {
        super.onBlockHit(hitResult);

        if (this.getEntityWorld().isClient()) {
            return;
        }

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
