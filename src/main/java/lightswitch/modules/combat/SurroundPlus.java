package lightswitch.modules.combat;

import lightswitch.Lightswitch;
import lightswitch.utils.world.BlockHelper;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SurroundPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgProtect = settings.createGroup("Protect");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> blockPerTick = sgGeneral.add(new IntSetting.Builder().name("blocks-per-tick").description("Block placements per tick.").defaultValue(4).min(1).sliderMax(10).build());
    private final Setting<Boolean> useDouble = sgGeneral.add(new BoolSetting.Builder().name("double").description("Place at your feet and head.").defaultValue(false).build());
    private final Setting<Boolean> placeInside = sgGeneral.add(new BoolSetting.Builder().name("place-on-crystal").description("Tries to place on a crystal if it's blocking your surround.").defaultValue(false).build());
    private final Setting<Boolean> groundOnly = sgGeneral.add(new BoolSetting.Builder().name("only-on-ground").description("Only activate when you're on the ground.").defaultValue(true).build());
    private final Setting<Boolean> sneakOnly = sgGeneral.add(new BoolSetting.Builder().name("require-sneak").description("Only activate while you're sneaking.").defaultValue(false).build());
    private final Setting<Boolean> disableAfter = sgGeneral.add(new BoolSetting.Builder().name("toggle-after").description("Disable after the surround is complete.").defaultValue(false).build());
    private final Setting<Boolean> centerPlayer = sgGeneral.add(new BoolSetting.Builder().name("center").description("Center you before starting the surround.").defaultValue(true).build());
    private final Setting<Boolean> disableJump = sgGeneral.add(new BoolSetting.Builder().name("toggle-on-jump").description("Disable if you jump.").defaultValue(true).build());
    private final Setting<Boolean> disableYchange = sgGeneral.add(new BoolSetting.Builder().name("toggle-on-y-change").description("Disable if your Y coord changes.").defaultValue(true).build());
    private final Setting<Boolean> rotation = sgGeneral.add(new BoolSetting.Builder().name("rotate").description("Rotate on block interactions.").defaultValue(false).build());
    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder().name("block").description("What blocks to use for surround.").defaultValue(Collections.singletonList(Blocks.OBSIDIAN)).filter(this::blockFilter).build());

    private final Setting<Boolean> protect = sgProtect.add(new BoolSetting.Builder().name("protect").description("Protect yourself from surround break/hold.").defaultValue(false).build());

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder().name("render").description("Renders where the surround will be placed.").defaultValue(true).build());
    private final Setting<Boolean> alwaysRender = sgRender.add(new BoolSetting.Builder().name("always").description("Render the surround blocks after they are placed.").defaultValue(false).visible(render :: get).build());
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>().name("shape-mode").description("How the shapes are rendered.").defaultValue(ShapeMode.Both).build());
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder().name("side-color").description("The side color.").defaultValue(new SettingColor(15, 255, 211,75)).build());
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder().name("line-color").description("The line color.").defaultValue(new SettingColor(15, 255, 211)).build());

    private final ArrayList<Vec3d> surr = new ArrayList<Vec3d>() {{
        add(new Vec3d(1, 0, 0));
        add(new Vec3d(-1, 0, 0));
        add(new Vec3d(0, 0, 1));
        add(new Vec3d(0, 0, -1));
    }};

    private final ArrayList<Vec3d> surrDouble = new ArrayList<Vec3d>() {{
        add(new Vec3d(1, 1, 0));
        add(new Vec3d(-1, 1, 0));
        add(new Vec3d(0, 1, 1));
        add(new Vec3d(0, 1, -1));
    }};


    public SurroundPlus() {
        super(Lightswitch.CATEGORY, "surround-plus", "Surround v2.");
    }

    @Override
    public void onActivate() {
        if (centerPlayer.get()) PlayerUtils.centerPlayer();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        int bpt = 0;
        if ((disableJump.get() && (mc.options.keyJump.isPressed() || mc.player.input.jumping)) || (disableYchange.get() && mc.player.prevY < mc.player.getY())) { toggle(); return; }
        if (groundOnly.get() && !mc.player.isOnGround()) return;
        if (sneakOnly.get() && !mc.options.keySneak.isPressed()) return;
        if (BlockHelper.isVecComplete(getSurrDesign())) {
            if (disableAfter.get()) {
                info("Surround Complete.");
                toggle();
            }
        } else {
            BlockPos ppos = mc.player.getBlockPos();
            for (Vec3d b : getSurrDesign()) {
                if (bpt >= blockPerTick.get()) return;
                BlockPos bb = ppos.add(b.x, b.y, b.z);
                if (BlockHelper.getBlock(bb) == Blocks.AIR) {
                    if (placeInside.get()) {
                        BlockUtils.place(bb, InvUtils.findInHotbar(itemStack -> blocks.get().contains(Block.getBlockFromItem(itemStack.getItem()))), rotation.get(), 100, false);
                    } else {
                        BlockUtils.place(bb, InvUtils.findInHotbar(itemStack -> blocks.get().contains(Block.getBlockFromItem(itemStack.getItem()))), rotation.get(), 100, true);
                    }
                    bpt++;
                }
            }
        }
        if (protect.get()) {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof EndCrystalEntity) {
                    BlockPos crystalPos = entity.getBlockPos();
                    if (isDangerousCrystal(crystalPos)) {
                        mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking()));
                        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                        return;
                    }
                }
            }
        }
    }

    private ArrayList<Vec3d> getSurrDesign() {
        ArrayList<Vec3d> surrDesign = new ArrayList<Vec3d>(surr);
        if (useDouble.get()) surrDesign.addAll(surrDouble);
        return surrDesign;
    }

    private boolean isDangerousCrystal(BlockPos bp) {
        BlockPos ppos = mc.player.getBlockPos();
        for (Vec3d b : getSurrDesign()) {
            BlockPos bb = ppos.add(b.x, b.y, b.z);
            if (!bp.equals(bb) && BlockHelper.distanceBetween(bb, bp) <= 2) return true;
        }
        return false;
    }


    private boolean blockFilter(Block block) {
        return block == Blocks.OBSIDIAN ||
            block == Blocks.CRYING_OBSIDIAN ||
            block == Blocks.NETHERITE_BLOCK ||
            block == Blocks.ENDER_CHEST ||
            block == Blocks.RESPAWN_ANCHOR;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (render.get()) {
            BlockPos ppos = mc.player.getBlockPos();
            for (Vec3d b: getSurrDesign()) {
                BlockPos bb = ppos.add(b.x, b.y, b.z);
                if (BlockHelper.getBlock(bb) == Blocks.AIR) event.renderer.box(bb, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                if (alwaysRender.get()) event.renderer.box(bb, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            }
        }
    }
}

