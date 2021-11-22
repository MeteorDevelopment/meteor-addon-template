package lightswitch.utils.player;


import net.minecraft.item.AirBlockItem;

import net.minecraft.item.ItemStack;


import static meteordevelopment.meteorclient.MeteorClient.mc;

public class InvHelper {

    public static Integer getEmptySlots() {
        int emptySlots = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);
            if (itemStack == null || itemStack.getItem() instanceof AirBlockItem) emptySlots++;
        }
        return emptySlots;
    }

    public static boolean isInventoryFull() {
        for (int i = 0; i < 36; i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);
            if (itemStack == null || itemStack.getItem() instanceof AirBlockItem) return false;
        }
        return true;
    }

    public static String getItemName(int slot) {
        return mc.player.getInventory().getStack(slot).getName().toString();
    }
}
