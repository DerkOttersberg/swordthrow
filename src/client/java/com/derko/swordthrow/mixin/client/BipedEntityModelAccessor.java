package com.derko.swordthrow.mixin.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BipedEntityModel.class)
public interface BipedEntityModelAccessor {
    @Invoker("getArm")
    ModelPart swordthrow$getArm(Arm arm);
}