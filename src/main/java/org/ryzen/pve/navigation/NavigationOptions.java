package org.ryzen.pve.navigation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record NavigationOptions(
   boolean allowBreak,
   boolean allowPlace,
   boolean allowSprint,
   boolean scanDroppedItems,
   int mineSearchRadius,
   boolean allowInteract,
   boolean fastMining,
   boolean protectClimbables,
   boolean strictBreakWhitelist,
   boolean rotateView
) {
   public NavigationOptions(
      boolean allowBreak,
      boolean allowPlace,
      boolean allowSprint,
      boolean scanDroppedItems,
      int mineSearchRadius,
      boolean allowInteract,
      boolean fastMining,
      boolean protectClimbables,
      boolean strictBreakWhitelist,
      boolean rotateView
   ) {
      mineSearchRadius = Math.max(0, mineSearchRadius);
      this.allowBreak = allowBreak;
      this.allowPlace = allowPlace;
      this.allowSprint = allowSprint;
      this.scanDroppedItems = scanDroppedItems;
      this.mineSearchRadius = mineSearchRadius;
      this.allowInteract = allowInteract;
      this.fastMining = fastMining;
      this.protectClimbables = protectClimbables;
      this.strictBreakWhitelist = strictBreakWhitelist;
      this.rotateView = rotateView;
   }

   public static NavigationOptions walking() {
      return new NavigationOptions(false, false, true, false, 0, true, false, true, false, true);
   }

   public static NavigationOptions mining() {
      return new NavigationOptions(true, true, true, true, 0, true, false, true, false, true);
   }

   public static NavigationOptions breakingOnly() {
      return new NavigationOptions(true, false, true, false, 15, false, true, true, true, true);
   }

   public NavigationOptions withViewRotation(boolean rotateView) {
      return new NavigationOptions(
         this.allowBreak,
         this.allowPlace,
         this.allowSprint,
         this.scanDroppedItems,
         this.mineSearchRadius,
         this.allowInteract,
         this.fastMining,
         this.protectClimbables,
         this.strictBreakWhitelist,
         rotateView
      );
   }
}
