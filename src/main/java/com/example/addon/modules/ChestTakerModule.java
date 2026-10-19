package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.OptionalInt;

public class ChestTakerModule extends Module {
    private static final int ACTION_INTERVAL_TICKS = 4;
    private static final int CHEST_TIMEOUT_TICKS = 1200;
    private static final int EMPTY_CHEST_WAIT_TICKS = 40;
    private static final double INTERACTION_DISTANCE_SQUARED = 25.0;

    private BlockPos targetChest;
    private ChestMenu targetMenu;
    private String requester;
    private String kitType;
    private int requestedAmount;
    private int initialShulkerCount;
    private int ticks;
    private int emptyChestTicks;
    private int actionCooldown;
    private int interactionCooldown;
    private boolean pathing;
    private boolean[] initialShulkerSlots;

    public ChestTakerModule() {
        super(AddonTemplate.CATEGORY, "chest-taker", "Collects requested shulker boxes from the chest behind the matching kit sign.");
    }

    @Override
    public void onDeactivate() {
        stopPathing();
        boolean hadRequest = requester != null;
        clearRequest();
        if (hadRequest) Modules.get().get(KitBotModule.class).cancelActiveRequest();
        targetMenu = null;
    }

    public boolean requestKit(String playerName, String requestedKit, int amount) {
        if (requester != null || mc.player == null || mc.level == null || mc.gameMode == null) return false;

        if (mc.gui.screen() instanceof ContainerScreen && mc.player.containerMenu != null) {
            mc.player.closeContainer();
        }

        requester = playerName;
        kitType = requestedKit;
        requestedAmount = amount;
        initialShulkerSlots = snapshotShulkerSlots();
        initialShulkerCount = countInventoryShulkers();
        ticks = 0;
        emptyChestTicks = 0;
        actionCooldown = 0;
        interactionCooldown = 0;
        targetMenu = null;

        if (!findAndPathToKitChest()) {
            clearRequest();
            warning("Could not find a loaded sign and chest for the %s kit within 64 blocks.", requestedKit);
            return false;
        }
        return true;
    }

    public void cancelKitPickup() {
        if (requester == null) return;
        stopPathing();
        clearRequest();
        targetMenu = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (requester == null) return;

        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            cancelKitPickup();
            return;
        }

        if (++ticks >= CHEST_TIMEOUT_TICKS) {
            warning("Timed out while collecting %s's %s kit.", requester, kitType);
            ChestMenu openMenu = mc.gui.screen() instanceof ContainerScreen screen
                && screen.getMenu() instanceof ChestMenu menu ? menu : null;
            failOutOfStock(openMenu);
            return;
        }

        if (!(mc.gui.screen() instanceof ContainerScreen screen)) {
            targetMenu = null;
            if (targetChest == null) return;

            if (interactionCooldown > 0) {
                interactionCooldown--;
            } else if (mc.player.distanceToSqr(Vec3.atCenterOf(targetChest)) <= INTERACTION_DISTANCE_SQUARED) {
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(targetChest), Direction.UP, targetChest, false);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                interactionCooldown = 20;
            }
            return;
        }

        if (!(screen.getMenu() instanceof ChestMenu menu)) return;
        if (menu != targetMenu) {
            if (targetChest == null) return;
            targetMenu = menu;
            targetChest = null;
            stopPathing();
            actionCooldown = ACTION_INTERVAL_TICKS;
        }

        if (actionCooldown > 0) {
            actionCooldown--;
            return;
        }

        int collected = countInventoryShulkers() - initialShulkerCount;
        if (collected >= requestedAmount) {
            finishPickup(collected);
            return;
        }

        int chestSlotCount = Math.max(0, menu.slots.size() - 36);
        for (int slot = 0; slot < chestSlotCount; slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (!ShulkerItemUtils.isShulker(stack)) continue;

            emptyChestTicks = 0;
            mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player);
            actionCooldown = ACTION_INTERVAL_TICKS;
            return;
        }

        if (++emptyChestTicks >= EMPTY_CHEST_WAIT_TICKS) {
            warning("The %s chest had %d of %d requested shulker(s).", kitType, collected, requestedAmount);
            failOutOfStock(menu);
        }
    }

    private boolean findAndPathToKitChest() {
        if (!BaritoneUtils.IS_AVAILABLE || PathManagers.get() == null) {
            warning("Baritone is not available; cannot reach the kit chest.");
            return false;
        }

        BlockPos playerPos = mc.player.blockPosition();
        int radius = 64;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -16; y <= 16; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos signPos = playerPos.offset(x, y, z);
                    if (mc.player.distanceToSqr(Vec3.atCenterOf(signPos)) > (double) radius * radius) continue;

                    var signState = mc.level.getBlockState(signPos);
                    if (!(signState.getBlock() instanceof WallSignBlock)) continue;
                    if (!(mc.level.getBlockEntity(signPos) instanceof SignBlockEntity sign)
                        || !signMatchesKit(sign, kitType)) continue;

                    BlockPos chestPos = signPos.relative(signState.getValue(WallSignBlock.FACING).getOpposite());
                    if (!(mc.level.getBlockState(chestPos).getBlock() instanceof ChestBlock)) continue;

                    targetChest = chestPos.immutable();
                    PathManagers.get().moveTo(targetChest, false);
                    pathing = true;
                    info("Pathing to the %s kit chest behind its sign.", kitType);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean signMatchesKit(SignBlockEntity sign, String requestedKit) {
        String token = switch (requestedKit) {
            case "pvp" -> "pvp";
            case "obsidian" -> "obsidian";
            case "totems" -> "totem";
            case "random" -> "random";
            case "build" -> "build";
            case "terracotta" -> "terracotta";
            case "random_concrete" -> "random_concrete";
            case "refill" -> "refill";
            default -> "";
        };
        if (token.isEmpty()) return false;

        for (boolean front : new boolean[] { true, false }) {
            for (var line : sign.getText(front).getMessages(false)) {
                if (line.getString().toLowerCase(Locale.ROOT).contains(token)) return true;
            }
        }
        return false;
    }

    private int countInventoryShulkers() {
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (ShulkerItemUtils.isShulker(stack)) count += stack.getCount();
        }
        return count;
    }

    private boolean[] snapshotShulkerSlots() {
        boolean[] slots = new boolean[36];
        for (int slot = 0; slot < slots.length; slot++) {
            slots[slot] = ShulkerItemUtils.isShulker(mc.player.getInventory().getItem(slot));
        }
        return slots;
    }

    private void finishPickup(int collected) {
        String playerName = requester;
        if (mc.player != null) mc.player.closeContainer();
        stopPathing();
        clearRequest();
        targetMenu = null;

        KitBotModule kitBot = Modules.get().get(KitBotModule.class);
        if (!kitBot.beginKitDelivery(playerName)) {
            warning("Collected the requested kit, but could not start the teleport for %s.", playerName);
            kitBot.cancelActiveRequest();
        }
    }

    private void failOutOfStock(ChestMenu menu) {
        String failedRequester = requester;
        if (menu != null && initialShulkerSlots != null) {
            for (int inventorySlot = 0; inventorySlot < initialShulkerSlots.length; inventorySlot++) {
                if (initialShulkerSlots[inventorySlot]
                    || !ShulkerItemUtils.isShulker(mc.player.getInventory().getItem(inventorySlot))) continue;

                OptionalInt menuSlot = menu.findSlot(mc.player.getInventory(), inventorySlot);
                menuSlot.ifPresent(slot -> mc.gameMode.handleContainerInput(
                    menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player));
            }
        }
        if (mc.player != null && mc.gui.screen() instanceof ContainerScreen) {
            mc.player.closeContainer();
        }
        stopPathing();
        clearRequest();
        targetMenu = null;
        Modules.get().get(KitBotModule.class).reportOutOfStock(failedRequester);
    }

    private void clearRequest() {
        targetChest = null;
        requester = null;
        kitType = null;
        requestedAmount = 0;
        initialShulkerCount = 0;
        initialShulkerSlots = null;
        ticks = 0;
        emptyChestTicks = 0;
        actionCooldown = 0;
        interactionCooldown = 0;
    }

    private void stopPathing() {
        if (pathing && BaritoneUtils.IS_AVAILABLE && PathManagers.get() != null) {
            PathManagers.get().stop();
        }
        pathing = false;
    }
}
