package lightswitch.utils.player;

import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.*;

import java.util.ArrayList;

public class ItemHelper {

    public static ArrayList<Item> wools = new ArrayList<Item>() {{
        add(Items.WHITE_WOOL);
        add(Items.ORANGE_WOOL);
        add(Items.MAGENTA_WOOL);
        add(Items.LIGHT_BLUE_WOOL);
        add(Items.YELLOW_WOOL);
        add(Items.LIME_WOOL);
        add(Items.PINK_WOOL);
        add(Items.GRAY_WOOL);
        add(Items.LIGHT_GRAY_WOOL);
        add(Items.CYAN_WOOL);
        add(Items.PURPLE_WOOL);
        add(Items.BLUE_WOOL);
        add(Items.BROWN_WOOL);
        add(Items.GREEN_WOOL);
        add(Items.RED_WOOL);
        add(Items.BLACK_WOOL);
    }};

    public static ArrayList<Item> planks = new ArrayList<Item>() {{
        add(Items.OAK_PLANKS);
        add(Items.SPRUCE_PLANKS);
        add(Items.BIRCH_PLANKS);
        add(Items.JUNGLE_PLANKS);
        add(Items.ACACIA_PLANKS);
        add(Items.DARK_OAK_PLANKS);
    }};

    public static ArrayList<Item> shulkers = new ArrayList<Item>() {{
        add(Items.SHULKER_BOX);
        add(Items.BLACK_SHULKER_BOX);
        add(Items.BLUE_SHULKER_BOX);
        add(Items.BROWN_SHULKER_BOX);
        add(Items.GREEN_SHULKER_BOX);
        add(Items.RED_SHULKER_BOX);
        add(Items.WHITE_SHULKER_BOX);
        add(Items.LIGHT_BLUE_SHULKER_BOX);
        add(Items.LIGHT_GRAY_SHULKER_BOX);
        add(Items.LIME_SHULKER_BOX);
        add(Items.MAGENTA_SHULKER_BOX);
        add(Items.ORANGE_SHULKER_BOX);
        add(Items.PINK_SHULKER_BOX);
        add(Items.CYAN_SHULKER_BOX);
        add(Items.GRAY_SHULKER_BOX);
        add(Items.PURPLE_SHULKER_BOX);
        add(Items.YELLOW_SHULKER_BOX);
    }};


    public static FindItemResult findShulker(boolean inventory) {
        if (inventory) return InvUtils.find(itemStack -> Block.getBlockFromItem(itemStack.getItem()) instanceof ShulkerBoxBlock);
        return InvUtils.findInHotbar(itemStack -> Block.getBlockFromItem(itemStack.getItem()) instanceof ShulkerBoxBlock);
    }

    public static FindItemResult findPick() {
        return InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof PickaxeItem);
    }

    public static FindItemResult findSword() {
        return InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof SwordItem);
    }

    public static FindItemResult findAxe() {
        return InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof AxeItem);
    }

    public static FindItemResult findChorus() {
        return InvUtils.findInHotbar(Items.CHORUS_FRUIT);
    }

    public static FindItemResult findEgap() {
        return InvUtils.findInHotbar(Items.ENCHANTED_GOLDEN_APPLE);
    }

    public static FindItemResult findObby() {return InvUtils.findInHotbar(Blocks.OBSIDIAN.asItem());}

    public static FindItemResult findCraftTable() {return InvUtils.findInHotbar(Blocks.CRAFTING_TABLE.asItem());}

    public static FindItemResult findXP() {
        return InvUtils.findInHotbar(Items.EXPERIENCE_BOTTLE);
    }

    public static FindItemResult findXPinAll() {
        return InvUtils.find(Items.EXPERIENCE_BOTTLE);
    }

    public static String getCommonName(Item item) {
        if (item instanceof BedItem) return "Beds";
        if (item instanceof ExperienceBottleItem) return "XP";
        if (item instanceof EndCrystalItem) return "Crystals";
        if (item instanceof EnchantedGoldenAppleItem) return "EGaps";
        if (item instanceof EnderPearlItem) return "Pearls";
        if (item.equals(Items.TOTEM_OF_UNDYING)) return "Totems";
        if (Block.getBlockFromItem(item) == Blocks.OBSIDIAN) return "Obby";
        if (Block.getBlockFromItem(item) instanceof EnderChestBlock) return "Echests";
        return Names.get(item);
    }



}
