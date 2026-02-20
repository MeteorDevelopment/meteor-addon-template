package com.bananaddons.modules;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import com.bananaddons.BananaAddon;

import java.util.ArrayList;
import java.util.List;
public class AutoPortal extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final List<BlockPos> waitingForBreak = new ArrayList<>();
    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Ticks between each obsidian placement.")
        .defaultValue(1)
        .sliderRange(1, 20)
        .build()
    );
    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("How many blocks to place each tick.")
        .defaultValue(1)
        .sliderRange(1, 5)
        .build()
    );
    private final Setting<Boolean> render = sgGeneral.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders the portal frame as it's being placed.")
        .defaultValue(true)
        .build()
    );
    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the box is rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );
    private final Setting<SettingColor> sideColor = sgGeneral.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(100, 100, 255, 10))
        .build()
    );
    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(100, 100, 255, 255))
        .build()
    );
    private final List<BlockPos> portalBlocks = new ArrayList<>();
    private int delay = 0;
    private int index = 0;
    public AutoPortal() {
        super(BananaAddon.CATEGORY, "auto-portal", "For the Base Hunter who has places to be.");
    }
    @Override
    public void onActivate() {
        int obsidianCount = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.OBSIDIAN) {
                obsidianCount += mc.player.getInventory().getStack(i).getCount();
            }
        }
        if (obsidianCount < 10) {
            error("Not enough obsidian to build the portal (need at least 10)!");
            toggle();
            return;
        }
        portalBlocks.clear();
        index = 0;
        delay = 0;
        Direction forward = mc.player.getHorizontalFacing();
        Direction right = forward.rotateYClockwise();
        BlockPos standingPos = mc.player.getBlockPos();
        BlockPos blockBelow = standingPos.down();
        double blockHeight = mc.world.getBlockState(blockBelow).getCollisionShape(mc.world, blockBelow).getMax(Direction.Axis.Y);
        if (blockHeight < 1.0) {
            standingPos = standingPos.up();
        }
        BlockPos base = standingPos
            .offset(forward, 2)
            .offset(right, -1);
        int obsidianCheck = 0;
        List<BlockPos> checkPositions = List.of(
            base.offset(right, 1), base.offset(right, 2),
            base.offset(right, 0).up(1), base.offset(right, 0).up(2), base.offset(right, 0).up(3),
            base.offset(right, 3).up(1), base.offset(right, 3).up(2), base.offset(right, 3).up(3),
            base.offset(right, 1).up(4), base.offset(right, 2).up(4)
        );
        boolean obstructed = checkPositions.stream().anyMatch(pos -> !mc.world.getBlockState(pos).isReplaceable());
        if (obstructed) {
            error("Portal area obstructed. Move and try again.");
            portalBlocks.clear();
            portalBlocks.addAll(checkPositions);
            index = checkPositions.size();
            return;
        }
        for (BlockPos checkPos : checkPositions) {
            if (mc.world.getBlockState(checkPos).getBlock().asItem() == Items.OBSIDIAN) {
                obsidianCheck++;
            }
        }
        if (obsidianCheck >= checkPositions.size()) {
            error("A portal already exists here!");
            toggle();
            return;
        }
        portalBlocks.add(base.offset(right, 1));
        portalBlocks.add(base.offset(right, 2));
        for (int i = 1; i <= 3; i++) {
            portalBlocks.add(base.offset(right, 0).up(i));
        }
        for (int i = 1; i <= 3; i++) {
            portalBlocks.add(base.offset(right, 3).up(i));
        }
        portalBlocks.add(base.offset(right, 1).up(4));
        portalBlocks.add(base.offset(right, 2).up(4));
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.OBSIDIAN) {
                mc.player.getInventory().setSelectedSlot(i);
                break;
            }
        }
    }
    @Override
    public void onDeactivate() {
        portalBlocks.clear();
        index = 0;
        delay = 0;
    }
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (!(mc.player.getMainHandStack().getItem() instanceof BlockItem blockItem)) return;
        if (blockItem.getBlock().asItem() != Items.OBSIDIAN) return;
        if (index >= portalBlocks.size()) {
            toggle();
            return;
        }
        delay++;
        if (delay < placeDelay.get()) return;
        for (int i = 0; i < blocksPerTick.get() && index < portalBlocks.size(); i++, index++) {
            BlockPos pos = portalBlocks.get(index);
            if (!mc.world.getBlockState(pos).isReplaceable()) {
                if (!waitingForBreak.contains(pos) && mc.world.getBlockState(pos).getBlock().asItem() != Items.OBSIDIAN) {
                    if (mc.interactionManager != null) {
                        mc.interactionManager.attackBlock(pos, Direction.UP);
                        mc.player.swingHand(Hand.MAIN_HAND);
                        waitingForBreak.add(pos);
                    }
                }
                index--;
                return;
            }
            waitingForBreak.remove(pos);
            BlockHitResult bhr = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
            mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
                Hand.OFF_HAND, bhr, mc.player.currentScreenHandler.getRevision() + 2));
            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
            mc.player.swingHand(Hand.MAIN_HAND);
        }
        delay = 0;
        if (index >= portalBlocks.size()) {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).getItem() == Items.FLINT_AND_STEEL) {
                    mc.player.getInventory().setSelectedSlot(i);
                    BlockPos firePos = portalBlocks.get(0).up();
                    BlockHitResult fireHit = new BlockHitResult(Vec3d.ofCenter(firePos), Direction.UP, firePos, false);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, fireHit);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    break;
                }
            }
            info("Portal complete. AutoPortal disabled.");
            toggle();
        }
    }
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) return;
        for (int i = index; i < portalBlocks.size(); i++) {
            BlockPos pos = portalBlocks.get(i);
            event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }
}
