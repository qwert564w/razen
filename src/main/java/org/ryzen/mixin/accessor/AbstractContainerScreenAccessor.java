package org.ryzen.mixin.accessor;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin({HandledScreen.class})
public interface AbstractContainerScreenAccessor {
   @Accessor("y")
   int getTopPos();

   @Accessor("x")
   int getLeftPos();

   @Accessor("backgroundWidth")
   int getImageWidth();

   @Accessor("backgroundHeight")
   int getImageHeight();

   @Accessor("focusedSlot")
   Slot getHoveredSlot();
}
