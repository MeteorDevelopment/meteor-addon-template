package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.Vec3;

public class CoordinateChangeKillModule extends Module {
    private static final long POST_BARITONE_GRACE_NANOS = 5_000_000_000L;
    private static final double KILL_MOVEMENT_DISTANCE_SQUARED = 100.0;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> ignoreBaritone = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-baritone")
        .description("Ignore coordinate changes while Baritone is pathing.")
        .defaultValue(true)
        .build()
    );

    private Vec3 lastPosition;
    private boolean wasBaritonePathing;
    private long ignoreMovementUntilNanos;

    public CoordinateChangeKillModule() {
        super(AddonTemplate.CATEGORY, "coordinate-change-kill", "Sends /kill when your position changes by more than 10 blocks at once.");
    }

    @Override
    public void onActivate() {
        lastPosition = mc.player == null ? null : mc.player.position();
        wasBaritonePathing = false;
        ignoreMovementUntilNanos = 0;
    }

    @Override
    public void onDeactivate() {
        lastPosition = null;
        wasBaritonePathing = false;
        ignoreMovementUntilNanos = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.player.connection == null) {
            lastPosition = null;
            wasBaritonePathing = false;
            ignoreMovementUntilNanos = 0;
            return;
        }
        Vec3 currentPosition = mc.player.position();
        long now = System.nanoTime();
        boolean baritonePathing = ignoreBaritone.get()
            && PathManagers.get() != null && PathManagers.get().isPathing();
        if (baritonePathing) {
            wasBaritonePathing = true;
            lastPosition = currentPosition;
            return;
        }
        if (wasBaritonePathing) {
            wasBaritonePathing = false;
            ignoreMovementUntilNanos = now + POST_BARITONE_GRACE_NANOS;
        }
        if (ignoreBaritone.get() && now < ignoreMovementUntilNanos) {
            lastPosition = currentPosition;
            return;
        }

        boolean movedMoreThanThreshold = lastPosition != null
            && currentPosition.distanceToSqr(lastPosition) > KILL_MOVEMENT_DISTANCE_SQUARED;
        if (movedMoreThanThreshold) {
            mc.player.connection.sendCommand("kill");
            info("Moved more than 10 blocks at once; sent /kill.");
            Modules.get().get(KitBotModule.class).onCoordinateChangeKillSent();
        }
        lastPosition = currentPosition;
    }
}
