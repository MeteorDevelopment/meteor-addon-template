package lightswitch.utils.misc;

import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.item.BedItem;
import net.minecraft.item.Items;

public class ItemCounter {

    public static int crystals() {
        return InvUtils.find(Items.END_CRYSTAL).getCount();
    }

    public static int egaps() {
        return InvUtils.find(Items.ENCHANTED_GOLDEN_APPLE).getCount();
    }

    public static int xp() {
        return InvUtils.find(Items.EXPERIENCE_BOTTLE).getCount();
    }

    public static int totem() {
        return InvUtils.find(Items.TOTEM_OF_UNDYING).getCount();
    }

    public static int beds() {
        return InvUtils.find(itemStack -> itemStack.getItem() instanceof BedItem).getCount();
    }

}
