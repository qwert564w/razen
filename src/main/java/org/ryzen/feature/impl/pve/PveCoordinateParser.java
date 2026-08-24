package org.ryzen.feature.impl.pve;

import java.util.Locale;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;

@Environment(EnvType.CLIENT)
final class PveCoordinateParser {
   private PveCoordinateParser() {
   }

   static Optional<BlockPos> parse(String value) {
      if (value == null) {
         return Optional.empty();
      } else {
         String normalized = value.trim().toLowerCase(Locale.ROOT);
         if (!normalized.isEmpty() && !normalized.equals("auto")) {
            normalized = normalized.replace("(", "").replace(")", "").replace("[", "").replace("]", "");
            String[] parts = normalized.split("[,;\\s]+");
            if (parts.length != 3) {
               return Optional.empty();
            } else {
               try {
                  return Optional.of(new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
               } catch (NumberFormatException var4) {
                  return Optional.empty();
               }
            }
         } else {
            return Optional.empty();
         }
      }
   }
}
