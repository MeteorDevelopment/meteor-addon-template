package com.bananaddons.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import com.bananaddons.BananaAddon;

import java.util.ArrayList;
import java.util.List;

public class AntiFeetPlace extends Module {
    public AntiFeetPlace() {
        super(BananaAddon.CATEGORY, "AntiFeetPlace",
            "Interrupts Enemies FeetPlace with ender-chests.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilters = settings.createGroup("Filters");

    // -------------------- General Settings -------------------- //
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range").defaultValue(5.2).min(1).sliderMax(8)
        .description("Max target range.").build());

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate").defaultValue(true)
        .description("Rotate during taps / placement / bedrock hold.").build());

    private final Setting<Integer> tapTicksBelow = sgGeneral.add(new IntSetting.Builder()
        .name("tap-ticks-below").defaultValue(1).min(1).sliderMax(8)
        .description("Ticks to tap the block under surround before moving on.").build());

    private final Setting<Integer> tapTicksSurround = sgGeneral.add(new IntSetting.Builder()
        .name("tap-ticks-surround").defaultValue(1).min(1).sliderMax(8)
        .description("Ticks to tap the surround so rebreak targets it before placing.").build());

    private final Setting<Boolean> renderPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("render-placement").defaultValue(true)
        .description("Let BlockUtils render intended placement.").build());

    private final Setting<Boolean> disableAfterPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-after-place").defaultValue(false)
        .description("If true, toggle off immediately after placing the ender chest.").build());

    private final Setting<Integer> postPlacePause = sgGeneral.add(new IntSetting.Builder()
        .name("post-place-pause").defaultValue(40).min(0).sliderMax(200)
        .visible(() -> !disableAfterPlace.get())
        .description("If not disabling, HOLD (do nothing) this many ticks so AutoMine can work.").build());

    private final Setting<Boolean> waitWhileChestPresent = sgGeneral.add(new BoolSetting.Builder()
        .name("wait-while-chest-present").defaultValue(true)
        .description("While an ender chest sits under the chosen surround, don't mine below again.").build());

    private final Setting<Integer> maxWaitChestTicks = sgGeneral.add(new IntSetting.Builder()
        .name("max-wait-chest-ticks").defaultValue(0).min(0).sliderMax(2000)
        .description("0 = wait indefinitely. If >0, leave WAIT_CHEST after this many ticks.")
        .visible(waitWhileChestPresent::get).build());

    private final Setting<Boolean> retargetWhileChestPresent = sgGeneral.add(new BoolSetting.Builder()
        .name("retarget-while-chest-present").defaultValue(true)
        .description("Tap the surround periodically while the chest exists so AutoMine keeps that hole open.").build());

    private final Setting<Integer> waitRetargetInterval = sgGeneral.add(new IntSetting.Builder()
        .name("wait-retarget-interval").defaultValue(3).min(1).sliderMax(20)
        .description("How often (ticks) to tap the surround while waiting on the chest.")
        .visible(retargetWhileChestPresent::get).build());

    private final Setting<Boolean> placeBridge = sgGeneral.add(new BoolSetting.Builder()
        .name("place-obsidian-bridge").defaultValue(true)
        .description("Place obsidian beside the chest (same Y) – any available side.").build());

    private final Setting<Boolean> bridgeMineIfBlocking = sgGeneral.add(new BoolSetting.Builder()
        .name("bridge-mine-if-blocking").defaultValue(true)
        .description("If the bridge spot or its clearance is blocked, briefly mine to clear.")
        .visible(placeBridge::get).build());

    private final Setting<Integer> bridgePrepMaxTicks = sgGeneral.add(new IntSetting.Builder()
        .name("bridge-prep-max-ticks").defaultValue(25).min(0).sliderMax(200)
        .description("Max ticks to try clearing the bridge spot / clearance.")
        .visible(placeBridge::get).build());

    private final Setting<Boolean> ensureCrystalClearance = sgGeneral.add(new BoolSetting.Builder()
        .name("ensure-crystal-clearance").defaultValue(true)
        .description("Ensure 2 air blocks above the bridge so a crystal can be placed.")
        .visible(placeBridge::get).build());

    private final Setting<Boolean> allowBedrockBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("allow-bedrock-break").defaultValue(true)
        .description("If true, will hold-mine bedrock under surround (your server allows it).").build());

    private final Setting<Boolean> skipBedrockBottomLayer = sgGeneral.add(new BoolSetting.Builder()
        .name("skip-bottom-bedrock-layer").defaultValue(true)
        .description("Skip bedrock at the world's bottommost Y (void floor).").build());

    private final Setting<Integer> bedrockHoldMaxTicks = sgGeneral.add(new IntSetting.Builder()
        .name("bedrock-hold-max-ticks").defaultValue(160).min(20).sliderMax(600)
        .description("Abort hold-mining if bedrock didn't break within this many ticks.").build());

    private final Setting<Integer> mineRateLimit = sgGeneral.add(new IntSetting.Builder()
        .name("mine-rate-limit-ticks").defaultValue(3).min(1).sliderMax(10)
        .description("Min ticks between any tap/mine packets to avoid SpeedMine resets & kicks.").build());

    // -------------------- Filter Settings -------------------- //
    private final Setting<Boolean> ignoreFriends = sgFilters.add(new BoolSetting.Builder()
        .name("ignore-friends").defaultValue(true).build());

    private final Setting<Boolean> ignoreNakeds = sgFilters.add(new BoolSetting.Builder()
        .name("ignore-nakeds").defaultValue(false)
        .description("Ignore players with no armor.").build());

    private final Setting<Boolean> skipUnbreakableBelow = sgFilters.add(new BoolSetting.Builder()
        .name("skip-unbreakable-below").defaultValue(true)
        .description("Skip below blocks with hardness < 0 (except bedrock if allowed).").build());

    private final Setting<Boolean> skipHardBelow = sgFilters.add(new BoolSetting.Builder()
        .name("skip-hard-below").defaultValue(false)
        .description("Skip if below block is in the hard list.").build());

    private final Setting<List<Block>> extraHard = sgFilters.add(new BlockListSetting.Builder()
        .name("extra-hard-list")
        .defaultValue(List.of(Blocks.OBSIDIAN, Blocks.ENDER_CHEST, Blocks.ANCIENT_DEBRIS, Blocks.RESPAWN_ANCHOR))
        .visible(skipHardBelow::get).build());

    // -------------------- State Machine -------------------- //
    private enum Stage { SELECT, MINE_BELOW_TAP, BREAK_BEDROCK_HOLD, RETARGET_SURROUND_TAP, PLACE_ECHEST, PLACE_BRIDGE, HOLD, WAIT_CHEST }
    private Stage stage = Stage.SELECT;

    private BlockPos surroundPos = null;
    private BlockPos belowPos = null;
    private Direction outwardDir = null;

    private int tapCounter = 0;
    private int ticksCounter = 0;
    private int tickCounter = 0;
    private int nextMineAllowedAt = 0;

    // -------------------- Lifecycle -------------------- //
    @Override
    public void onDeactivate() {
        resetAll();
    }

    // -------------------- Event Handlers -------------------- //
    @EventHandler
    private void onTick(TickEvent.Post e) {
        if (mc.player == null || mc.world == null) return;

        tickCounter++;

        switch (stage) {
            case SELECT -> selectStage();
            case MINE_BELOW_TAP -> mineBelowTapStage();
            case BREAK_BEDROCK_HOLD -> breakBedrockHoldStage();
            case RETARGET_SURROUND_TAP -> retargetSurroundTapStage();
            case PLACE_ECHEST -> placeEChestStage();
            case PLACE_BRIDGE -> placeBridgeStage();
            case HOLD -> holdStage();
            case WAIT_CHEST -> waitChestStage();
        }
    }

    // -------------------- Stage Implementations -------------------- //
    private void selectStage() {
        PlayerEntity target = nearestEnemy();
        if (target == null) { resetAll(); return; }

        BlockPos feet = target.getBlockPos();
        Direction facingMe = dirTowardPlayer(feet);
        BlockPos preferredSurround = feet.offset(facingMe);

        BlockPos best = null; double bestDist = Double.MAX_VALUE; Direction bestDir = null;

        if (preferredSurround.getY() == feet.getY() && preferredSurround.getManhattanDistance(feet) == 1) {
            BlockState s = mc.world.getBlockState(preferredSurround);
            if (!s.isAir()) {
                BlockPos below = preferredSurround.down();
                BlockState bs = mc.world.getBlockState(below);
                boolean ok = true;

                if (bs.isOf(Blocks.ENDER_CHEST) && waitWhileChestPresent.get()) { best = preferredSurround; bestDir = facingMe; bestDist = 0; }
                else {
                    if (skipUnbreakableBelow.get()) {
                        float hardness = bs.getHardness(mc.world, below);
                        if (hardness < 0f && !bs.isOf(Blocks.BEDROCK)) ok = false;
                    }
                    if (ok && skipHardBelow.get() && extraHard.get().contains(bs.getBlock()) && !bs.isOf(Blocks.BEDROCK)) ok = false;
                    if (ok && bs.isOf(Blocks.BEDROCK) && allowBedrockBreak.get() && skipBedrockBottomLayer.get() && below.getY() <= mc.world.getBottomY()) ok = false;

                    Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                    double d = playerPos.squaredDistanceTo(Vec3d.ofCenter(preferredSurround));
                    if (ok && d <= range.get() * range.get()) { best = preferredSurround; bestDir = facingMe; bestDist = d; }
                }
            }
        }

        if (best == null) {
            for (BlockPos p : surroundOf(feet)) {
                if (p.getY() != feet.getY()) continue;
                if (p.getManhattanDistance(feet) != 1) continue;
                BlockState s = mc.world.getBlockState(p);
                if (s.isAir()) continue;

                BlockPos below = p.down();
                BlockState bs = mc.world.getBlockState(below);

                if (bs.isOf(Blocks.ENDER_CHEST) && waitWhileChestPresent.get()) { best = p; bestDir = dirFrom(feet, p); bestDist = 0; break; }

                if (skipUnbreakableBelow.get()) {
                    float hardness = bs.getHardness(mc.world, below);
                    if (hardness < 0f && !bs.isOf(Blocks.BEDROCK)) continue;
                }
                if (skipHardBelow.get() && extraHard.get().contains(bs.getBlock()) && !bs.isOf(Blocks.BEDROCK)) continue;
                if (bs.isOf(Blocks.BEDROCK) && allowBedrockBreak.get() && skipBedrockBottomLayer.get() && below.getY() <= mc.world.getBottomY()) continue;

                Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                double d = playerPos.squaredDistanceTo(Vec3d.ofCenter(p));
                if (d <= range.get() * range.get() && d < bestDist) { best = p; bestDir = dirFrom(feet, p); bestDist = d; }
            }
        }

        if (best == null) { resetAll(); return; }

        surroundPos = best;
        belowPos = best.down();
        outwardDir = bestDir;
        tapCounter = 0;
        ticksCounter = 0;

        BlockState belowState = mc.world.getBlockState(belowPos);

        if (belowState.isOf(Blocks.ENDER_CHEST)) {
            stage = waitOrYieldAfterChest();
        } else if (belowState.isOf(Blocks.BEDROCK) && allowBedrockBreak.get()) {
            if (skipBedrockBottomLayer.get() && belowPos.getY() <= mc.world.getBottomY()) { stage = Stage.SELECT; return; }
            stage = Stage.BREAK_BEDROCK_HOLD;
        } else if (!belowState.isAir()) {
            stage = Stage.MINE_BELOW_TAP;
        } else {
            stage = Stage.RETARGET_SURROUND_TAP;
        }
    }

    private void mineBelowTapStage() {
        if (!validTarget()) { stage = Stage.SELECT; return; }

        BlockState bs = mc.world.getBlockState(belowPos);
        if (bs.isOf(Blocks.ENDER_CHEST)) { stage = waitOrYieldAfterChest(); return; }
        if (bs.isAir()) { tapCounter = 0; stage = Stage.RETARGET_SURROUND_TAP; return; }
        if (bs.isOf(Blocks.BEDROCK) && allowBedrockBreak.get()) {
            if (!(skipBedrockBottomLayer.get() && belowPos.getY() <= mc.world.getBottomY())) {
                tapCounter = 0; stage = Stage.BREAK_BEDROCK_HOLD; return;
            } else { stage = Stage.SELECT; return; }
        }

        tapBlockOnce(belowPos);
        if (++tapCounter >= tapTicksBelow.get()) {
            tapCounter = 0;
            stage = Stage.RETARGET_SURROUND_TAP;
        }
    }

    private void breakBedrockHoldStage() {
        if (!validTarget()) { stage = Stage.SELECT; return; }

        BlockState bs = mc.world.getBlockState(belowPos);
        if (bs.isAir()) { ticksCounter = 0; stage = Stage.RETARGET_SURROUND_TAP; return; }
        if (bs.isOf(Blocks.ENDER_CHEST)) { stage = waitOrYieldAfterChest(); return; }
        if (skipBedrockBottomLayer.get() && belowPos.getY() <= mc.world.getBottomY()) { stage = Stage.SELECT; return; }

        mineOnce(belowPos);
        if (++ticksCounter >= bedrockHoldMaxTicks.get()) stage = Stage.SELECT;
    }

    private void retargetSurroundTapStage() {
        if (!validTarget()) { stage = Stage.SELECT; return; }
        tapBlockOnce(surroundPos);
        if (++tapCounter >= tapTicksSurround.get()) {
            tapCounter = 0;
            stage = Stage.PLACE_ECHEST;
        }
    }

    private void placeEChestStage() {
        if (!validTarget()) { stage = Stage.SELECT; return; }

        BlockPos placeAt = belowPos;
        BlockState bs = mc.world.getBlockState(placeAt);
        if (bs.isOf(Blocks.ENDER_CHEST)) { stage = placeBridge.get() ? Stage.PLACE_BRIDGE : waitOrYieldAfterChest(); return; }

        FindItemResult chest = InvUtils.findInHotbar(Items.ENDER_CHEST);
        if (!chest.found()) chest = InvUtils.find(Items.ENDER_CHEST);
        if (!chest.found()) { stage = Stage.SELECT; return; }

        if (!BlockUtils.canPlace(placeAt)) {
            if (++tapCounter > 20) { tapCounter = 0; stage = Stage.SELECT; }
            return;
        }

        if (rotate.get()) {
            float[] yp = lookAt(Vec3d.ofCenter(placeAt));
            Rotations.rotate(yp[0], yp[1], 25, () -> {});
        }

        boolean placed = BlockUtils.place(placeAt, chest, false, 50, renderPlace.get());
        if (placed) {
            stage = placeBridge.get() ? Stage.PLACE_BRIDGE : waitOrYieldAfterChest();
        } else if (++tapCounter > 10) {
            tapCounter = 0; stage = Stage.SELECT;
        }
    }

    private void placeBridgeStage() {
        if (!validTarget() || !placeBridge.get()) {
            stage = waitOrYieldAfterChest(); return;
        }

        if (!mc.world.getBlockState(belowPos).isOf(Blocks.ENDER_CHEST)) {
            if (++ticksCounter < 5) return;
        }
        ticksCounter = 0;

        Direction bias = outwardDir != null ? outwardDir : Direction.NORTH;
        Direction[] order = new Direction[] { bias, bias.getOpposite(), rotateLeft(bias), rotateRight(bias) };

        for (Direction d : order) {
            BlockPos bridgeBase = belowPos.offset(d);

            if (mc.world.getBlockState(bridgeBase).isOf(Blocks.OBSIDIAN)) {
                stage = waitOrYieldAfterChest(); return;
            }

            if (ensureCrystalClearance.get()) {
                if (!clearOrWait(bridgeBase.up(), bridgePrepMaxTicks.get())) return;
                if (!clearOrWait(bridgeBase.up(2), bridgePrepMaxTicks.get())) return;
            }

            if (!mc.world.getBlockState(bridgeBase).isAir()) {
                if (!bridgeMineIfBlocking.get()) continue;
                if (!clearOrWait(bridgeBase, bridgePrepMaxTicks.get())) return;
            }

            FindItemResult obs = InvUtils.findInHotbar(Items.OBSIDIAN);
            if (!obs.found()) obs = InvUtils.find(Items.OBSIDIAN);
            if (!obs.found()) { stage = waitOrYieldAfterChest(); return; }

            if (rotate.get()) {
                float[] yp = lookAt(Vec3d.ofCenter(bridgeBase));
                Rotations.rotate(yp[0], yp[1], 25, () -> {});
            }
            if (BlockUtils.place(bridgeBase, obs, false, 50, renderPlace.get())) {
                stage = waitOrYieldAfterChest();
                return;
            }
        }

        if (++tapCounter > 10) { tapCounter = 0; stage = Stage.SELECT; }
    }

    private void holdStage() {
        if (!validTarget()) { stage = Stage.SELECT; return; }
        if (++ticksCounter >= postPlacePause.get()) stage = Stage.SELECT;
    }

    private void waitChestStage() {
        if (!validTarget()) { stage = Stage.SELECT; return; }

        BlockState under = mc.world.getBlockState(belowPos);
        boolean chestPresent = under.isOf(Blocks.ENDER_CHEST);

        if (retargetWhileChestPresent.get() && surroundPos != null) {
            if (!mc.world.getBlockState(surroundPos).isAir()
                && (tickCounter % Math.max(1, waitRetargetInterval.get()) == 0)) {
                tapBlockOnce(surroundPos);
            }
        }

        if (maxWaitChestTicks.get() > 0 && ++ticksCounter >= maxWaitChestTicks.get()) { stage = Stage.SELECT; return; }
        if (chestPresent) return;

        ticksCounter = 0;
        tapCounter = 0;

        BlockState bs = mc.world.getBlockState(belowPos);
        if (bs.isAir()) stage = Stage.RETARGET_SURROUND_TAP;
        else if (bs.isOf(Blocks.BEDROCK) && allowBedrockBreak.get()
            && !(skipBedrockBottomLayer.get() && belowPos.getY() <= mc.world.getBottomY())) {
            stage = Stage.BREAK_BEDROCK_HOLD;
        } else if (!bs.isAir() && !bs.isOf(Blocks.ENDER_CHEST)) {
            stage = Stage.MINE_BELOW_TAP;
        } else stage = Stage.SELECT;
    }

    // -------------------- Helpers -------------------- //
    private void resetAll() {
        stage = Stage.SELECT;
        surroundPos = null;
        belowPos = null;
        outwardDir = null;
        tapCounter = 0;
        ticksCounter = 0;
        nextMineAllowedAt = 0;
    }

    private Stage waitOrYieldAfterChest() {
        if (disableAfterPlace.get()) return nextAfterPlaced();
        if (waitWhileChestPresent.get()) { ticksCounter = 0; return Stage.WAIT_CHEST; }
        ticksCounter = 0; return Stage.HOLD;
    }

    private Stage nextAfterPlaced() {
        if (disableAfterPlace.get()) { this.toggle(); return Stage.SELECT; }
        ticksCounter = 0; return Stage.HOLD;
    }

    private boolean validTarget() {
        return surroundPos != null && belowPos != null && inRange(surroundPos);
    }

    private boolean inRange(BlockPos p) {
        double r = range.get() + 1.0;
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        return playerPos.squaredDistanceTo(Vec3d.ofCenter(p)) <= r * r;
    }

    private void tapBlockOnce(BlockPos pos) {
        if (tickCounter < nextMineAllowedAt) return;
        BlockState st = mc.world.getBlockState(pos);
        if (st.isAir() || st.isOf(Blocks.ENDER_CHEST)) return;
        if (rotate.get()) {
            float[] yp = lookAt(Vec3d.ofCenter(pos));
            Rotations.rotate(yp[0], yp[1], 25, () -> {});
        }
        Direction face = faceFor(pos);
        if (face == null) return;
        mc.interactionManager.updateBlockBreakingProgress(pos, face);
        mc.player.swingHand(Hand.MAIN_HAND);
        nextMineAllowedAt = tickCounter + mineRateLimit.get();
    }

    private void mineOnce(BlockPos pos) {
        if (tickCounter < nextMineAllowedAt) return;
        BlockState st = mc.world.getBlockState(pos);
        if (st.isAir() || st.isOf(Blocks.ENDER_CHEST)) return;
        if (rotate.get()) {
            float[] yp = lookAt(Vec3d.ofCenter(pos));
            Rotations.rotate(yp[0], yp[1], 25, () -> {});
        }
        Direction face = faceFor(pos);
        if (face == null) return;
        mc.interactionManager.updateBlockBreakingProgress(pos, face);
        mc.player.swingHand(Hand.MAIN_HAND);
        nextMineAllowedAt = tickCounter + mineRateLimit.get();
    }

    private boolean clearOrWait(BlockPos pos, int maxTicks) {
        if (mc.world.getBlockState(pos).isAir()) return true;
        if (!bridgeMineIfBlocking.get()) return false;
        if (ticksCounter++ >= maxTicks) { ticksCounter = 0; stage = waitOrYieldAfterChest(); return false; }
        mineOnce(pos);
        return false;
    }

    private Direction faceFor(BlockPos pos) {
        BlockHitResult bhr = mc.world.raycast(new RaycastContext(
            mc.player.getEyePos(),
            Vec3d.ofCenter(pos),
            RaycastContext.ShapeType.OUTLINE,
            RaycastContext.FluidHandling.NONE,
            mc.player
        ));
        return bhr != null ? bhr.getSide() : Direction.UP;
    }

    private float[] lookAt(Vec3d v) {
        Vec3d eye = mc.player.getEyePos();
        double dx = v.x - eye.x, dy = v.y - eye.y, dz = v.z - eye.z;
        double h = Math.sqrt(dx*dx + dz*dz);
        float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float)(-Math.toDegrees(Math.atan2(dy, h)));
        return new float[]{yaw, pitch};
    }

    private PlayerEntity nearestEnemy() {
        PlayerEntity best = null; double bestDist = Double.MAX_VALUE;

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !p.isAlive()) continue;
            if (ignoreFriends.get() && Friends.get().isFriend(p)) continue;

            if (ignoreNakeds.get()) {
                if (p.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
                    && p.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
                    && p.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
                    && p.getEquippedStack(EquipmentSlot.FEET).isEmpty()) continue;
            }

            double d = mc.player.distanceTo(p);
            if (d <= range.get() && d < bestDist) { best = p; bestDist = d; }
        }
        return best;
    }

    private List<BlockPos> surroundOf(BlockPos feet) {
        List<BlockPos> list = new ArrayList<>(4);
        list.add(feet.north());
        list.add(feet.south());
        list.add(feet.east());
        list.add(feet.west());
        return list;
    }

    private Direction dirFrom(BlockPos feet, BlockPos surround) {
        if (surround.equals(feet.north())) return Direction.NORTH;
        if (surround.equals(feet.south())) return Direction.SOUTH;
        if (surround.equals(feet.east()))  return Direction.EAST;
        if (surround.equals(feet.west()))  return Direction.WEST;
        return null;
    }

    private Direction dirTowardPlayer(BlockPos enemyFeet) {
        Vec3d my = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3d tgt = Vec3d.ofCenter(enemyFeet);
        double dx = my.x - tgt.x, dz = my.z - tgt.z;
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? Direction.EAST : Direction.WEST;
        else return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private Direction rotateLeft(Direction d) {
        return switch (d) {
            case NORTH -> Direction.WEST;
            case WEST -> Direction.SOUTH;
            case SOUTH -> Direction.EAST;
            case EAST -> Direction.NORTH;
            default -> d;
        };
    }

    private Direction rotateRight(Direction d) {
        return switch (d) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            default -> d;
        };
    }
}
