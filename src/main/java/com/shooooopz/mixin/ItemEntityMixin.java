package com.shooooopz.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin extends Entity {

    @Shadow public abstract ItemStack getItem();
    @Shadow private int age;
    @Shadow public abstract void setItem(ItemStack stack);

    public ItemEntityMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        if (this.level().isClientSide()) return;

        // 1. Update Visuals
        updateCustomName();

        // 2. Logic
        if (this.age % 2 == 0) {
            mergeNearbyItems();
        }
    }

    @Unique
    private void updateCustomName() {
        ItemStack stack = this.getItem();
        int count = stack.getCount();

        if (count > 0) {
            // Part 1: The Number "64x " (Gold + Bold)
            Component countText = Component.literal(count + "x ")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

            // Part 2: The Name (White + Bold)
            Component nameText = stack.getHoverName().copy()
                    .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);

            this.setCustomName(countText.copy().append(nameText));
            this.setCustomNameVisible(true);
        } else {
            this.setCustomNameVisible(false);
        }
    }

    @Unique
    private void mergeNearbyItems() {
        double radius = 5.0;
        ItemEntity thisEntity = (ItemEntity)(Object)this;

        List<ItemEntity> nearbyItems = this.level().getEntitiesOfClass(
          ItemEntity.class,
          this.getBoundingBox().inflate(radius),
                entity -> entity != thisEntity && entity.isAlive()
        );

        for (ItemEntity other : nearbyItems) {
            if (canMerge(thisEntity, other)) {

                double distance = thisEntity.distanceTo(other);
                if (distance > 1.0) {
                    suckItemIn(thisEntity, other);
                } else {
                    tryMerge(thisEntity, other);
                }
            }
        }
    }

    @Unique
    private void suckItemIn(ItemEntity target, ItemEntity other) {
        Vec3 targetPos = target.position();
        Vec3 otherPos = other.position();

        // Math: Calculate direction vector pointing from Other -> Target
        Vec3 vector = targetPos.subtract(otherPos);

        // Normalize (make length 1) and scale (speed)
        // 0.05 is a gentle slide. 0.2 is a fast snap.
        Vec3 motion = vector.normalize().scale(0.05);

        // Add this motion to the item's existing movement
        other.setDeltaMovement(other.getDeltaMovement().add(motion));
    }

    @Unique
    private boolean canMerge(ItemEntity item1, ItemEntity item2) {
        ItemStack stack1 = item1.getItem();
        ItemStack stack2 = item2.getItem();

        return !stack1.isEmpty() && !stack2.isEmpty()
                && ItemStack.isSameItemSameComponents(stack1, stack2)
                && stack1.getCount() + stack2.getCount() <= stack1.getMaxStackSize();
    }

    @Unique
    private void tryMerge(ItemEntity target, ItemEntity other) {
        ItemStack stackTarget = target.getItem();
        ItemStack stackOther =other.getItem();

        int space = stackTarget.getMaxStackSize() - stackTarget.getCount();
        int amountToMove = Math.min(space, stackOther.getCount());

        if (amountToMove > 0) {
            ItemStack newTargetStack = stackTarget.copy();
            ItemStack newOtherStack = stackOther.copy();

            newOtherStack.shrink(amountToMove);
            newTargetStack.grow(amountToMove);

            target.setItem(newTargetStack);
            other.setItem(newOtherStack);

            if (newOtherStack.isEmpty()) {
                other.discard();
            }
        }
    }
}


