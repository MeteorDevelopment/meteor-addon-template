package com.bananaddons.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import com.bananaddons.BananaAddon;
import com.bananaddons.utils.BananaUtils;

public class ArmorNotify extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> threshold = sgGeneral.add(new IntSetting.Builder()
        .name("durability")
        .description("How low an armor piece needs to be to alert you (in %).")
        .defaultValue(10)
        .min(1)
        .sliderMin(1)
        .sliderMax(100)
        .max(100)
        .build()
    );

    public ArmorNotify() {
        super(BananaAddon.CATEGORY, "armor-notify", "Notifies you when your armor pieces are low.");
    }

    private boolean alertedHelm;
    private boolean alertedChest;
    private boolean alertedLegs;
    private boolean alertedBoots;

    @Override
    public void onActivate() {
        alertedHelm = false;
        alertedChest = false;
        alertedLegs = false;
        alertedBoots = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        double thresholdValue = threshold.get();
        assert mc.player != null;

        ItemStack helmet = mc.player.getEquippedStack(EquipmentSlot.HEAD);
        ItemStack chestplate = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        ItemStack leggings = mc.player.getEquippedStack(EquipmentSlot.LEGS);
        ItemStack boots = mc.player.getEquippedStack(EquipmentSlot.FEET);

        checkArmorPiece(helmet, "Your helmet dura is low!", thresholdValue, 0);
        checkArmorPiece(chestplate, "Your chestplate dura is low!", thresholdValue, 1);
        checkArmorPiece(leggings, "Your leggings dura is low!", thresholdValue, 2);
        checkArmorPiece(boots, "Your boots dura is low!", thresholdValue, 3);
    }

    private void checkArmorPiece(ItemStack armor, String message, double thresholdValue, int slot) {
        boolean isLow = BananaUtils.checkThreshold(armor, thresholdValue);
        boolean alerted = getAlertStatus(slot);

        if (isLow && !alerted) {
            warning(message);
            setAlertStatus(slot, true);
        } else if (!isLow && alerted) {
            setAlertStatus(slot, false);
        }
    }

    private boolean getAlertStatus(int slot) {
        return switch (slot) {
            case 0 -> alertedHelm;
            case 1 -> alertedChest;
            case 2 -> alertedLegs;
            case 3 -> alertedBoots;
            default -> false;
        };
    }

    private void setAlertStatus(int slot, boolean value) {
        switch (slot) {
            case 0 -> alertedHelm = value;
            case 1 -> alertedChest = value;
            case 2 -> alertedLegs = value;
            case 3 -> alertedBoots = value;
        }
    }
}
