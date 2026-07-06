package dev.leonetic.mixin.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FireworkRocketEntity.class)
public interface FireworkRocketEntityAccessor {

    @Accessor("life")
    int homovore$getLife();

    @Accessor("lifetime")
    int homovore$getLifetime();

    @Accessor("attachedToEntity")
    LivingEntity homovore$getAttachedToEntity();

    @Accessor("DATA_ID_FIREWORKS_ITEM")
    static EntityDataAccessor<ItemStack> homovore$getFireworksItemData() {
        throw new AssertionError();
    }
}
