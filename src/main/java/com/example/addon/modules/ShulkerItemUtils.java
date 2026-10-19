package com.example.addon.modules;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

final class ShulkerItemUtils {
    private ShulkerItemUtils() {
    }

    static boolean isShulker(ItemStack stack) {
        return !stack.isEmpty()
            && stack.getItem() instanceof BlockItem blockItem
            && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    static boolean isNamedShulker(ItemStack stack, String name) {
        return stack.getCustomName() != null
            && isShulker(stack)
            && stack.getCustomName().getString().equalsIgnoreCase(name.trim());
    }

    static boolean containsItem(ItemStack stack, Item item) {
        if (!isShulker(stack)) return false;

        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        return contents != null && contents.nonEmptyItemCopyStream().anyMatch(itemStack -> itemStack.is(item));
    }
}
