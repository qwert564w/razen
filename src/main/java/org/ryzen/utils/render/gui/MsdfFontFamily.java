package org.ryzen.utils.render.gui;

import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class MsdfFontFamily {
   private final NavigableMap<Integer, Identifier> variants;
   private final Map<Integer, MsdfFont> resolved = new ConcurrentHashMap<>();

   private MsdfFontFamily(NavigableMap<Integer, Identifier> variants) {
      this.variants = variants;
   }

   public static MsdfFontFamily.Builder builder() {
      return new MsdfFontFamily.Builder();
   }

   public MsdfFont resolve(int weight) {
      int clamped = Math.max(1, Math.min(1000, weight));
      return this.resolved.computeIfAbsent(clamped, w -> MsdfFont.load(this.resolveVariant(w)));
   }

   public MsdfFont regular() {
      return this.resolve(400);
   }

   private Identifier resolveVariant(int weight) {
      Identifier exact = this.variants.get(Integer.valueOf(weight));
      if (exact != null) {
         return exact;
      } else if (weight >= 400 && weight <= 500) {
         Entry<Integer, Identifier> inRange = this.variants.ceilingEntry(weight);
         if (inRange != null && inRange.getKey() <= 500) {
            return inRange.getValue();
         } else {
            Entry<Integer, Identifier> below = this.variants.floorEntry(weight);
            return below != null ? below.getValue() : this.variants.firstEntry().getValue();
         }
      } else if (weight < 400) {
         Entry<Integer, Identifier> below = this.variants.floorEntry(weight);
         return below != null ? below.getValue() : this.variants.firstEntry().getValue();
      } else {
         Entry<Integer, Identifier> above = this.variants.ceilingEntry(weight);
         return above != null ? above.getValue() : this.variants.lastEntry().getValue();
      }
   }

   @Environment(EnvType.CLIENT)
   public static final class Builder {
      private final NavigableMap<Integer, Identifier> variants = new TreeMap<>();

      private Builder() {
      }

      public MsdfFontFamily.Builder variant(int weight, String metadataId) {
         return this.variant(weight, Identifier.of(metadataId));
      }

      public MsdfFontFamily.Builder variant(int weight, Identifier metadataId) {
         if (weight >= 1 && weight <= 1000) {
            this.variants.put(Integer.valueOf(weight), Objects.requireNonNull(metadataId, "metadataId"));
            return this;
         } else {
            throw new IllegalArgumentException("Font weight out of range: " + weight);
         }
      }

      public MsdfFontFamily build() {
         if (this.variants.isEmpty()) {
            throw new IllegalStateException("Font family needs at least one variant");
         } else {
            return new MsdfFontFamily(new TreeMap<>(this.variants));
         }
      }
   }
}
