package org.ryzen.mixin.gui;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.ryzen.feature.impl.misc.AuctionHelperFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({HandledScreen.class})
public abstract class AbstractContainerScreenMixin {
   @Inject(
      method = {"drawSlot"},
      at = {@At("TAIL")}
   )
   private void renderAuctionRank(DrawContext graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
      if (slot != null && slot.hasStack()) {
         HandledScreen<?> screen = (HandledScreen<?>)(Object)this;
         int color = AuctionHelperFeature.slotOverlayColor(screen.getTitle().getString(), slot.getStack());
         if (color != 0) {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
         }
      }
   }

   @Inject(
      method = {"getTooltipFromItem"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void augmentAuctionTooltip(ItemStack stack, CallbackInfoReturnable<List<Text>> cir) {
      HandledScreen<?> screen = (HandledScreen<?>)(Object)this;
      cir.setReturnValue(AuctionHelperFeature.augmentTooltip(screen.getTitle().getString(), stack, (List<Text>)cir.getReturnValue()));
   }
}
