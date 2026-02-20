package com.bananaddons.modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import com.bananaddons.BananaAddon;
import com.bananaddons.mixin.accessor.PlayerInventoryAccessor;
import com.bananaddons.mixin.accessor.UpdateSelectedSlotS2CPacketAccessor;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.ScaffoldingBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import com.bananaddons.utils.RotationUtils;
import com.bananaddons.utils.PlacementUtils;
import com.bananaddons.utils.InventoryManager;
import com.bananaddons.utils.BananaUtils;

public class Phase extends Module {
    private final SettingGroup sgPearl = settings.createGroup("Pearl");

    private final Setting<Integer> pitch = sgPearl.add(new IntSetting.Builder()
        .name("pitch")
        .description("The pitch angle to throw pearls.")
        .defaultValue(85)
        .range(70, 90)
        .build()
    );
    private final Setting<Boolean> swapAlternative = sgPearl.add(new BoolSetting.Builder()
        .name("swap-alternative")
        .description("Uses inventory swap for swapping to pearls.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> attack = sgPearl.add(new BoolSetting.Builder()
        .name("attack")
        .description("Attacks entities in the way of the pearl phase.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> swing = sgPearl.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swings the hand when throwing pearls.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> selfFill = sgPearl.add(new BoolSetting.Builder()
        .name("self-fill")
        .description("Automatically fills blocks you are phasing on.")
        .defaultValue(false)
        .build()
    );

    private InventoryManager inventoryManager;

    public Phase() {
        super(BananaAddon.CATEGORY, "phase", "Allows player to phase through solid blocks using ender pearls.");
        inventoryManager = InventoryManager.getInstance();
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }
        performPearlPhase();
        toggle();
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) {
            mc.player.noClip = false;
        }
    }

    @EventHandler
    private void onPushOutOfBlocks(BananaUtils event) {
        if (isActive()) {
            event.cancel();
        }
    }

    private void performPearlPhase() {
        int pearlSlot = PlacementUtils.getEnderPearlSlot();
        if (pearlSlot == -1 || mc.player.getItemCooldownManager().isCoolingDown(Items.ENDER_PEARL.getDefaultStack())) {
            return;
        }
        final Vec3d pearlTargetVec = new Vec3d(Math.floor(mc.player.getX()) + 0.5, 0.0, Math.floor(mc.player.getZ()) + 0.5);
        float[] rotations = RotationUtils.getRotationsTo(mc.player.getEyePos(), pearlTargetVec);
        float yaw = rotations[0] + 180.0f;

        if (attack.get()) {
            handlePearlAttacks(yaw);
        }
        if (selfFill.get()) {
            handleSelfFill(yaw);
        }

        RotationUtils rotationManager = RotationUtils.getInstance();
        int targetSlot;
        if (swapAlternative.get()) {
            targetSlot = ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot();
            performInventorySwapPVP(pearlSlot);
        } else if (pearlSlot < 9) {
            targetSlot = pearlSlot;
        } else {
            return;
        }

        inventoryManager.setSlot(targetSlot, InventoryManager.Priority.PEARL_PHASE);
        rotationManager.setRotationSilent(yaw, pitch.get());
        mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, yaw, pitch.get()));

        if (swing.get()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        } else {
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }

        if (swapAlternative.get()) {
            performInventorySwapPVP(pearlSlot);
        }

        inventoryManager.syncToClient();
        rotationManager.setRotationSilentSync();
    }

    private void handlePearlAttacks(float yaw) {
        BlockHitResult hitResult = (BlockHitResult) mc.player.raycast(3.0, 0, false);
        Box searchBox = Box.from(Vec3d.ofCenter(hitResult.getBlockPos())).expand(0.2);
        for (Entity entity : mc.world.getOtherEntities(null, searchBox)) {
            if (entity instanceof ItemFrameEntity itemFrame) {
                mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking()));
                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            }
        }
        BlockState state = mc.world.getBlockState(mc.player.getBlockPos());
        if (state.getBlock() instanceof ScaffoldingBlock) {
            BlockPos pos = mc.player.getBlockPos();
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP));
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP));
        }
    }

    private void handleSelfFill(float yaw) {
        float yaw1 = yaw % 360.0f;
        if (yaw1 < 0.0f) {
            yaw1 += 360.0f;
        }
        BlockPos blockPos = mc.player.getBlockPos();
        if (yaw1 >= 22.5 && yaw1 < 67.5) {
            blockPos = blockPos.south().west();
        } else if (yaw1 >= 67.5 && yaw1 < 112.5) {
            blockPos = blockPos.west();
        } else if (yaw1 >= 112.5 && yaw1 < 157.5) {
            blockPos = blockPos.north().west();
        } else if (yaw1 >= 157.5 && yaw1 < 202.5) {
            blockPos = blockPos.north();
        } else if (yaw1 >= 202.5 && yaw1 < 247.5) {
            blockPos = blockPos.north().east();
        } else if (yaw1 >= 247.5 && yaw1 < 292.5) {
            blockPos = blockPos.east();
        } else if (yaw1 >= 292.5 && yaw1 < 337.5) {
            blockPos = blockPos.south().east();
        } else {
            blockPos = blockPos.south();
        }
        FindItemResult resistantBlock = PlacementUtils.findResistantBlock();
        if (resistantBlock.found() && blockPos != null && !mc.world.getBlockState(blockPos.down()).isReplaceable()) {
            RotationUtils rotationManager = RotationUtils.getInstance();
            PlacementUtils.placeBlock(blockPos, true, true, true);
        }
    }

    private void performInventorySwapPVP(int pearlSlot) {
        mc.interactionManager.clickSlot(0, pearlSlot < 9 ? pearlSlot + 36 : pearlSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(0, ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot() + 36, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(0, pearlSlot < 9 ? pearlSlot + 36 : pearlSlot, 0, SlotActionType.PICKUP, mc.player);
    }

    @Override
    public String getInfoString() {
        return "Pearl Mode";
    }
}
