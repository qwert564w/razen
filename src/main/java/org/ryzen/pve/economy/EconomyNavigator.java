package org.ryzen.pve.economy;

import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.ryzen.feature.impl.pve.PveManagerFeature;
import org.ryzen.pve.navigation.NavigationOptions;
import org.ryzen.pve.navigation.Navigator;

@Environment(EnvType.CLIENT)
public final class EconomyNavigator {
   private final Navigator navigator;
   private BlockPos goal;
   private boolean begun;

   public EconomyNavigator(Navigator navigator) {
      this.navigator = Objects.requireNonNull(navigator, "navigator");
   }

   public boolean moveTo(ClientPlayerEntity player, BlockPos target, int radius) {
      if (player != null && target != null) {
         if (arrived(player, target, Math.max(1.5, (double)radius + 0.75))) {
            this.cancel();
            return true;
         } else if (!this.navigator.isAvailable()) {
            return false;
         } else {
            if (!this.begun) {
               this.navigator.begin(PveManagerFeature.INSTANCE.configureNavigation(NavigationOptions.walking()));
               this.begun = true;
            }

            BlockPos immutable = target.toImmutable();
            if (!immutable.equals(this.goal) || !this.navigator.isPathing()) {
               this.goal = immutable;
               this.navigator.pathTo(immutable, Math.max(1, radius));
            }

            return false;
         }
      } else {
         return false;
      }
   }

   public void cancel() {
      if (this.begun) {
         this.navigator.cancel();
      }

      this.goal = null;
   }

   public void close() {
      if (this.begun) {
         this.navigator.end();
      }

      this.begun = false;
      this.goal = null;
   }

   public static boolean arrived(ClientPlayerEntity player, BlockPos target, double distance) {
      return player != null && target != null && target.getSquaredDistanceFromCenter(player.getX(), player.getY(), player.getZ()) <= distance * distance;
   }
}
