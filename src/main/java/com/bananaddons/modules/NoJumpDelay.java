package com.bananaddons.modules;

import com.bananaddons.BananaAddon;
import com.bananaddons.utils.ILivingEntity;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class NoJumpDelay extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Settings to configure the module
    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("delay")
            .description("The delay between jumps (0 for no delay).")
            .defaultValue(0)
            .min(0)
            .sliderMax(10)
            .build()
    );

    private final Setting<Boolean> vanilla = sgGeneral.add(new BoolSetting.Builder()
            .name("vanilla")
            .description("Uses a vanilla-like bypass if enabled.")
            .defaultValue(false)
            .build()
    );

    public NoJumpDelay() {
        // Uses the BananaAddon category constant as requested
        super(BananaAddon.CATEGORY, "no-jump-delay", "Removes the delay between jumps.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        // Accessing the internal Minecraft living entity jump delay field
        // Note: Field mapping may vary based on your Minecraft/Intermediary version
        if (!vanilla.get()) {
            ((ILivingEntity) mc.player).setJumpTicks(delay.get());
        }
    }
}
