package com.bananaddons.mixin;

import com.bananaddons.utils.ILivingEntity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements ILivingEntity {
    @Shadow private int jumpingCooldown; // This is the 'jump delay' field in Yarn mappings

    @Override
    public void setJumpTicks(int ticks) {
        this.jumpingCooldown = ticks;
    }
}
