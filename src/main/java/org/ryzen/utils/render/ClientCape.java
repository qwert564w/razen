package org.ryzen.utils.render;

import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Identifier;
import net.minecraft.util.AssetInfo.TextureAsset;
import org.ryzen.context.MinecraftContext;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.impl.visual.CapeFeature;

@Environment(EnvType.CLIENT)
public final class ClientCape {
   private ClientCape() {
   }

   public static boolean shouldForceCape(UUID playerUuid) {
      if (MinecraftContext.mc.player == null) {
         return false;
      } else {
         CapeFeature feature = getFeature();
         return feature != null && feature.isEnabled() ? MinecraftContext.mc.player.getUuid().equals(playerUuid) : false;
      }
   }

   public static SkinTextures apply(SkinTextures skin) {
      CapeFeature feature = getFeature();
      String fileName = feature != null ? feature.currentStyle().getFileName() : "glass.png";
      final Identifier dynamicTextureId = Identifier.of("ryzen", "textures/cape/" + fileName);
      TextureAsset dynamicTexture = new TextureAsset() {
         public Identifier id() {
            return dynamicTextureId;
         }

         public Identifier texturePath() {
            return dynamicTextureId;
         }
      };
      return new SkinTextures(skin.body(), dynamicTexture, skin.elytra(), skin.model(), skin.secure());
   }

   private static CapeFeature getFeature() {
      return FeatureManager.INSTANCE.getFeature(CapeFeature.class);
   }
}
