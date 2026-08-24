package org.ryzen.feature.impl.combat;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.feature.setting.MultiSelectSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.mixin.accessor.KeyMappingAccessor;

@Environment(EnvType.CLIENT)
public final class AutoClickerFeature extends Feature {
   public final BooleanSetting leftMouse = this.register(new BooleanSetting("Left Mouse", true));
   public final BooleanSetting rightMouse = this.register(new BooleanSetting("Right Mouse", false));
   public final ModeSetting clickMode = this.register(new ModeSetting("Mode", "Normal", "Normal", "Jitter", "Butterfly"));
   public final MultiSelectSetting targets = this.register(
      new MultiSelectSetting("Targets", List.of("Players", "Mobs"), "Players", "Mobs", "Animals", "Invisible")
   );
   public final NumberSetting cps = this.register(new NumberSetting("CPS", 12.0, 1.0, 20.0, 1.0, ""));
   private long nextLeftClickAt;
   private long nextRightClickAt;
   private boolean butterflyFast;

   public AutoClickerFeature() {
      super("AutoClicker", "Automatic mouse clicking", FeatureCategory.COMBAT, -1);
   }

   @Override
   protected void onDisable() {
      this.nextLeftClickAt = 0L;
      this.nextRightClickAt = 0L;
      this.butterflyFast = false;
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      if (client.player != null && client.world != null && client.currentScreen == null && !client.player.isRiding()) {
         long now = System.nanoTime();
         if (this.leftMouse.getValue() && now >= this.nextLeftClickAt && this.validCrosshairTarget(client.targetedEntity)) {
            click(client.options.attackKey);
            this.nextLeftClickAt = now + this.nextDelayNanos();
         }

         if (this.rightMouse.getValue() && now >= this.nextRightClickAt) {
            click(client.options.useKey);
            this.nextRightClickAt = now + this.nextDelayNanos();
         }
      }
   }

   private boolean validCrosshairTarget(Entity entity) {
      if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
         return false;
      }

      if (living.isInvisible() && !this.targets.isSelected("Invisible")) {
         return false;
      } else if (living instanceof PlayerEntity) {
         return this.targets.isSelected("Players");
      } else {
         return living instanceof PassiveEntity ? this.targets.isSelected("Animals") : living instanceof MobEntity && this.targets.isSelected("Mobs");
      }
   }

   private long nextDelayNanos() {
      double baseMillis = 1000.0 / this.cps.getValue();
      String var5 = this.clickMode.getValue();

      double multiplier = switch (var5) {
         case "Jitter" -> ThreadLocalRandom.current().nextDouble(0.85, 1.16);
         case "Butterfly" -> {
            this.butterflyFast = !this.butterflyFast;
            yield this.butterflyFast ? 0.65 : 1.35;
         }
         default -> 1.0;
      };
      return Math.max(1L, Math.round(baseMillis * multiplier * 1000000.0));
   }

   private static void click(KeyBinding mapping) {
      KeyBinding.onKeyPressed(((KeyMappingAccessor)mapping).getKey());
   }
}
