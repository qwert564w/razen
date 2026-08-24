package org.ryzen.feature;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum FeatureCategory {
   COMBAT("Combat"),
   MOVEMENT("Movement"),
   VISUAL("Visual"),
   PLAYER("Player"),
   MISC("Misc"),
   PVE("PVE");

   private final String displayName;

   private FeatureCategory(String displayName) {
      this.displayName = displayName;
   }
   public String getDisplayName() {
      return this.displayName;
   }
}
