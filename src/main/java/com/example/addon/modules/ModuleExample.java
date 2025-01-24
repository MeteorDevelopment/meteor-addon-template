package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class ModuleExample extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("Render");

    /**
     * Example setting.
     * The {@code name} parameter should be in kebab-case.
     * If you want to access the setting from another class, simply make the setting {@code public}, and use
     * {@link meteordevelopment.meteorclient.systems.modules.Modules#get(Class)} to access the {@link Module} object.
     */
    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("The size of the marker.")
        .defaultValue(2.0d)
        .range(0.5d, 10.0d)
        .build()
    );

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .description("The color of the marker.")
        .defaultValue(Color.MAGENTA)
        .build()
    );

    /**
     * The {@code name} parameter should be in kebab-case.
     */
    public ModuleExample() {
        super(AddonTemplate.CATEGORY, "world-origin", "An example module that highlights the center of the world.");
    }

    /**
     * Example event handling method.
     * Requires {@link AddonTemplate#getPackage()} to be setup correctly, otherwise the game will crash whenever the module is enabled.
     */
    @EventHandler
    private void onRender3d(Render3DEvent event) {
        // Create & stretch the marker object
        Box marker = new Box(BlockPos.ORIGIN);
        marker = marker.stretch(
            scale.get() * marker.getLengthX(),
            scale.get() * marker.getLengthY(),
            scale.get() * marker.getLengthZ()
        );

        // Render the marker based on the color setting
        event.renderer.box(marker, color.get(), color.get(), ShapeMode.Both, 0);
    }
}
