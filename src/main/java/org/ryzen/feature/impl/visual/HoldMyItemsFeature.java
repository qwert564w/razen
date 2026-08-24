package org.ryzen.feature.impl.visual;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import org.ryzen.event.EventManager;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.lifecycle.ClientStartEvent;
import org.ryzen.event.events.lifecycle.ResourceReloadEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.menu.core.MenuOverlay;

@Environment(EnvType.CLIENT)
public final class HoldMyItemsFeature extends Feature {
   private static final String RESOURCE_PACK_ID = "holdmyitems:pack_test";
   private static final HoldMyItemsFeature.ReloadCompletionListener RELOAD_COMPLETION = new HoldMyItemsFeature.ReloadCompletionListener();

   public HoldMyItemsFeature() {
      super("HoldMyItems", "Animated hands and held items in first person", FeatureCategory.VISUAL, -1);
   }

   public static boolean isActive() {
      return FeatureManager.INSTANCE.getEnabled(HoldMyItemsFeature.class) != null;
   }

   @Override
   protected void onEnable() {
      syncResourcePack(true);
   }

   @Override
   protected void onDisable() {
      syncResourcePack(false);
   }

   @EventTarget
   public void onClientStart(ClientStartEvent event) {
      syncResourcePack(true);
   }

   private static void syncResourcePack(boolean enabled) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null) {
         client.execute(() -> {
            ResourcePackManager repository = client.getResourcePackManager();
            repository.scanPacks();
            boolean selected = repository.getEnabledIds().contains("holdmyitems:pack_test");
            boolean changed = enabled ? !selected && repository.enable("holdmyitems:pack_test") : selected && repository.disable("holdmyitems:pack_test");
            if (changed) {
               boolean restoreMenu = MenuOverlay.suspendForReload(client);
               if (restoreMenu) {
                  RELOAD_COMPLETION.arm();
               }

               try {
                  client.options.refreshResourcePacks(repository);
               } catch (RuntimeException var7) {
                  if (restoreMenu) {
                     RELOAD_COMPLETION.complete(client);
                  }

                  throw var7;
               }
            }
         });
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class ReloadCompletionListener {
      private boolean armed;

      private void arm() {
         if (!this.armed) {
            this.armed = true;
            EventManager.subscribe(this);
         }
      }

      private void complete(MinecraftClient client) {
         if (this.armed) {
            this.armed = false;
            EventManager.unsubscribe(this);
            MenuOverlay.resumeAfterReload(client);
         }
      }

      @EventTarget
      public void onResourceReload(ResourceReloadEvent event) {
         this.complete(event.getClient());
      }
   }
}
