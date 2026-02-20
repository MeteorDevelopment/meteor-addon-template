package com.bananaddons.mixin.accessor;

import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(UpdateSelectedSlotS2CPacket.class)
public interface UpdateSelectedSlotS2CPacketAccessor {
    @Accessor("slot")
    int getSlot();
}
