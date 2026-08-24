package org.ryzen.mixin.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.ryzen.mixin.accessor.AbstractContainerScreenAccessor;
import org.ryzen.utils.inventory.DropAllInventoryController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({InventoryScreen.class})
public abstract class InventoryScreenMixin extends Screen {
   protected InventoryScreenMixin(Text title) {
      super(title);
   }

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void addDropAllButton(CallbackInfo ci) {
      AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor)(Object)this;
      ButtonWidget button = ButtonWidget.builder(Text.literal("Выбросить всё"), ignored -> DropAllInventoryController.toggle((InventoryScreen)(Object)this))
         .dimensions((this.width - 110) / 2, Math.max(4, accessor.getTopPos() - 24), 110, 20)
         .build();
      this.addDrawableChild(button);
      DropAllInventoryController.bindButton(button);
   }
}
