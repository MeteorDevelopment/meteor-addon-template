package com.bananaddons.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import com.bananaddons.BananaAddon;

import java.util.*;

public class SurroundPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlace = settings.createGroup("Place Logic");
    private final SettingGroup sgCenter = settings.createGroup("Center Logic");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
            .name("blocks")
            .description("Blocks to use for surrounding.")
            .defaultValue(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.NETHERITE_BLOCK)
            .build()
    );

    private final Setting<Boolean> tagSwitch = sgGeneral.add(new BoolSetting.Builder()
            .name("tag-switch")
            .description("Disables the module immediately after placing missing blocks.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Integer> delay = sgPlace.add(new IntSetting.Builder()
            .name("place-delay")
            .description("Tick delay between block placements.")
            .defaultValue(0)
            .min(0)
            .sliderMax(10)
            .build()
    );

    private final Setting<Integer> blocksPerTick = sgPlace.add(new IntSetting.Builder()
            .name("blocks-per-tick")
            .description("Maximum blocks to place per tick.")
            .defaultValue(4)
            .min(1)
            .sliderMax(8)
            .build()
    );

    private final Setting<Boolean> rotate = sgPlace.add(new BoolSetting.Builder()
            .name("rotate")
            .description("Sends rotation packets when placing (Crucial for GrimAC).")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> strict = sgPlace.add(new BoolSetting.Builder()
            .name("strict-directions")
            .description("Only places on visible block faces to bypass strict anti-cheats.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> support = sgPlace.add(new BoolSetting.Builder()
            .name("support")
            .description("Places a block under your feet if open air.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> onlyOnGround = sgPlace.add(new BoolSetting.Builder()
            .name("only-on-ground")
            .description("Only activates when you are on the ground.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> disableOnJump = sgPlace.add(new BoolSetting.Builder()
            .name("disable-on-jump")
            .description("Automatically disables the module if you jump.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> disableOnYChange = sgPlace.add(new BoolSetting.Builder()
            .name("disable-on-y-change")
            .description("Disables if your Y level changes.")
            .defaultValue(true)
            .build()
    );

    private final Setting<CenterMode> centerMode = sgCenter.add(new EnumSetting.Builder<CenterMode>()
            .name("center-mode")
            .description("Method used to center the player.")
            .defaultValue(CenterMode.NCP)
            .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
            .name("render")
            .description("Renders the block placements.")
            .defaultValue(true)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Both)
            .visible(render::get)
            .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color")
            .description("The side color.")
            .defaultValue(new SettingColor(145, 60, 255, 75))
            .visible(render::get)
            .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color")
            .description("The line color.")
            .defaultValue(new SettingColor(145, 60, 255, 255))
            .visible(render::get)
            .build()
    );

    private final Setting<Boolean> fade = sgRender.add(new BoolSetting.Builder()
            .name("fade")
            .description("Fades the rendered block over time.")
            .defaultValue(true)
            .visible(render::get)
            .build()
    );

    private final Setting<Double> fadeTime = sgRender.add(new DoubleSetting.Builder()
            .name("fade-time")
            .description("How long the fade lasts in seconds.")
            .defaultValue(0.5)
            .min(0.1)
            .sliderMax(2)
            .visible(() -> render.get() && fade.get())
            .build()
    );

    private final Map<BlockPos, Long> renderMap = new HashMap<>();
    private int delayTimer;
    private BlockPos initialPos;

    public SurroundPlus() {
        super(BananaAddon.CATEGORY, "surround-plus", "Surrounds feet with Obsidian using strict logic.");
    }

    @Override
    public void onActivate() {
        delayTimer = 0;
        renderMap.clear();
        if (mc.player == null) return;
        initialPos = mc.player.getBlockPos();

        if (centerMode.get() == CenterMode.Teleport) {
            PlayerUtils.centerPlayer();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if ((disableOnJump.get() && mc.options.jumpKey.isPressed()) || (disableOnYChange.get() && mc.player.getY() != initialPos.getY())) {
            toggle();
            return;
        }

        if (onlyOnGround.get() && !mc.player.isOnGround()) return;

        handleCentering();

        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        FindItemResult block = InvUtils.findInHotbar(itemStack -> blocks.get().contains(Block.getBlockFromItem(itemStack.getItem())));
        if (!block.found()) return;

        int placed = 0;
        BlockPos playerPos = mc.player.getBlockPos();

        if (support.get()) {
            BlockPos underPos = playerPos.down();
            if (mc.world.getBlockState(underPos).isReplaceable()) {
                if (placeBlock(underPos, block)) {
                    placed++;
                }
            }
        }

        BlockPos[] offsets = {
                playerPos.north(),
                playerPos.south(),
                playerPos.east(),
                playerPos.west()
        };

        boolean allPlaced = true;

        for (BlockPos pos : offsets) {
            if (!BlockUtils.canPlace(pos)) {
                if (mc.world.getBlockState(pos).isReplaceable()) allPlaced = false;
                continue;
            }

            if (placed >= blocksPerTick.get()) {
                allPlaced = false;
                break;
            }

            if (placeBlock(pos, block)) {
                placed++;
            } else {
                allPlaced = false;
            }
        }

        if (placed > 0) {
            delayTimer = delay.get();
        }

        if (tagSwitch.get() && allPlaced) {
            toggle();
        }
    }

    private boolean placeBlock(BlockPos pos, FindItemResult item) {
        if (BlockUtils.place(pos, item, rotate.get(), 50, true)) {
            renderMap.put(pos, System.currentTimeMillis());
            return true;
        }
        return false;
    }

    private void handleCentering() {
        if (centerMode.get() != CenterMode.NCP) return;

        Vec3d centerPos = Vec3d.ofBottomCenter(mc.player.getBlockPos());
        double xDiff = Math.abs(centerPos.x - mc.player.getX());
        double zDiff = Math.abs(centerPos.z - mc.player.getZ());

        if (xDiff <= 0.1 && zDiff <= 0.1) {
            mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
            return;
        }

        double motionX = (centerPos.x - mc.player.getX()) / 2.0;
        double motionZ = (centerPos.z - mc.player.getZ()) / 2.0;

        mc.player.setVelocity(motionX, mc.player.getVelocity().y, motionZ);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || renderMap.isEmpty()) return;

        renderMap.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue() > fadeTime.get() * 1000);

        renderMap.forEach((pos, time) -> {
            double progress = 1.0;
            if (fade.get()) {
                long alive = System.currentTimeMillis() - time;
                progress = 1.0 - MathHelper.clamp((double) alive / (fadeTime.get() * 1000), 0.0, 1.0);
            }

            SettingColor sColor = new SettingColor(sideColor.get());
            SettingColor lColor = new SettingColor(lineColor.get());

            sColor.a = (int) (sColor.a * progress);
            lColor.a = (int) (lColor.a * progress);

            event.renderer.box(pos, sColor, lColor, shapeMode.get(), 0);
        });
    }

    public enum CenterMode {
        Teleport,
        NCP,
        None
    }
}
