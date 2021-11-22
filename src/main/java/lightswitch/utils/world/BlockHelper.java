package lightswitch.utils.world;

import lightswitch.utils.player.AutomationUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class BlockHelper {


    public static boolean isVecComplete(ArrayList<Vec3d> vlist) {
        BlockPos ppos = mc.player.getBlockPos();
        for (Vec3d b: vlist) {
            BlockPos bb = ppos.add(b.x, b.y, b.z);
            if (getBlock(bb) == Blocks.AIR) return false;
        }
        return true;
    }

    public static List<BlockPos> getSphere(BlockPos centerPos, int radius, int height) {
        ArrayList<BlockPos> blocks = new ArrayList<>();
        for (int i = centerPos.getX() - radius; i < centerPos.getX() + radius; i++) {
            for (int j = centerPos.getY() - height; j < centerPos.getY() + height; j++) {
                for (int k = centerPos.getZ() - radius; k < centerPos.getZ() + radius; k++) {
                    BlockPos pos = new BlockPos(i, j, k);
                    if (distanceBetween(centerPos, pos) <= radius && !blocks.contains(pos)) blocks.add(pos);
                }
            }
        }
        return blocks;
    }


    public static double distanceBetween(BlockPos pos1, BlockPos pos2) {
        double d = pos1.getX() - pos2.getX();
        double e = pos1.getY() - pos2.getY();
        double f = pos1.getZ() - pos2.getZ();
        return MathHelper.sqrt((float) (d * d + e * e + f * f));
    }


    public static BlockPos getBlockPosFromDirection(Direction direction, BlockPos orginalPos) {
        return switch (direction) {
            case UP -> orginalPos.up();
            case DOWN -> orginalPos.down();
            case EAST -> orginalPos.east();
            case WEST -> orginalPos.west();
            case NORTH -> orginalPos.north();
            case SOUTH -> orginalPos.south();
        };
    }


    public static Block getBlock(BlockPos p) {
        if (p == null) return null;
        return mc.world.getBlockState(p).getBlock();
    }

    public static BlockPos getCityBlock(PlayerEntity target, Boolean randomize) {
        if (target == null || mc.player.distanceTo(target) > 4.8) return null;
        BlockPos targetBlock;
        List<BlockPos> surroundBlocks = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            if (direction == Direction.UP || direction == Direction.DOWN) continue;
            BlockPos pos = target.getBlockPos().offset(direction);
            if (AutomationUtils.isSurroundBlock(pos)) surroundBlocks.add(pos);
        }
        if (surroundBlocks.isEmpty()) return null;
        surroundBlocks.sort(Comparator.comparingDouble(PlayerUtils::distanceTo));
        int counter = 0;
        while (true) {
            if (counter >= 4) return null;
            BlockPos possibleTarget;
            if (randomize) {
                possibleTarget = surroundBlocks.get(new Random().nextInt(surroundBlocks.size()));
            } else {
                possibleTarget = surroundBlocks.get(counter);
            }
            if (!isOurSurroundBlock(possibleTarget) && !outOfRange(possibleTarget)) {
                targetBlock = possibleTarget;
                break;
            }
            counter++;
        }
        return targetBlock;
    }

    public static boolean isOurSurroundBlock(BlockPos bp) {
        BlockPos ppos = mc.player.getBlockPos();
        for (Direction direction : Direction.values()) {
            if (direction == Direction.UP || direction == Direction.DOWN) continue;
            BlockPos pos = ppos.offset(direction);
            if (pos.equals(bp)) return true;
        }
        return false;
    }

    public static boolean outOfRange(BlockPos cityBlock) {
        return MathHelper.sqrt((float) mc.player.squaredDistanceTo(cityBlock.getX(), cityBlock.getY(), cityBlock.getZ())) > mc.interactionManager.getReachDistance();
    }
}
