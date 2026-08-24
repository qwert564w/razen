package org.ryzen.feature.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.lifecycle.FeatureToggleEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.feature.setting.NumberSetting;

@Environment(EnvType.CLIENT)
public final class ToggleSoundsFeature extends Feature {
   private static final String TYPE_1 = "Type 1";
   private static final String TYPE_2 = "Type 2";
   private static final String TYPE_3 = "Type 3";
   private static final String TYPE_4 = "Type 4";
   public final ModeSetting type = this.register(new ModeSetting("Sound", "Type 1", "Type 1", "Type 2", "Type 3", "Type 4"));
   public final NumberSetting volume = this.register(new NumberSetting("Volume", 75.0, 0.0, 100.0, 1.0, "%"));

   public ToggleSoundsFeature() {
      super("ToggleSounds", "Plays a sound when a feature is toggled", FeatureCategory.MISC, -1);
   }

   @EventTarget
   public void onFeatureToggle(FeatureToggleEvent event) {
      if (event.getFeature() != this) {
         float volumeScale = this.volume.getValue().floatValue() / 100.0F;
         if (!(volumeScale <= 0.0F)) {
            boolean on = event.isEnabled();
            String typeName = this.type.getValue();

            SoundEvent sound = switch (typeName) {
               case "Type 2" -> (SoundEvent)SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
               case "Type 3" -> (SoundEvent)SoundEvents.UI_BUTTON_CLICK.value();
               case "Type 4" -> SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME;
               default -> (SoundEvent)SoundEvents.BLOCK_NOTE_BLOCK_HAT.value();
            };
            float pitch = on ? 1.4F : 0.8F;
            MinecraftClient client = MinecraftClient.getInstance();
            client.getSoundManager().play(PositionedSoundInstance.ui(sound, pitch, volumeScale));
         }
      }
   }
}
