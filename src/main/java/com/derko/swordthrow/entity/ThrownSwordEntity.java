package com.derko.swordthrow.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

public class ThrownSwordEntity extends ThrownItemEntity {
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
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);

        if (!this.getEntityWorld().isClient() && hitResult.getType() != HitResult.Type.ENTITY) {
            this.dropAsItemAndDiscard();
        }
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
