package org.ryzen.feature.impl.player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.event.events.lifecycle.WorldLeaveEvent;
import org.ryzen.event.events.packet.PacketReceiveEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.MultiSelectSetting;
import org.ryzen.utils.text.ChatUtil;

@Environment(EnvType.CLIENT)
public final class UseTrackerFeature extends Feature implements MinecraftContext {
   private static final String TOTEMS = "Totems";
   private static final String POTIONS = "Potions";
   private static final String ITEMS = "Items";
   private static final byte USE_TOTEM_STATUS = 35;
   public final MultiSelectSetting tracked = this.register(new MultiSelectSetting("Track", List.of("Totems", "Potions", "Items"), "Totems", "Potions", "Items"));
   private final Set<Integer> announcedUses = new HashSet<>();

   public UseTrackerFeature() {
      super("Use Tracker", "Reports totems, potions and consumables used by nearby players", FeatureCategory.PLAYER, -1);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      if (event.getClient().world != null && event.getClient().player != null) {
         Set<Integer> stillUsing = new HashSet<>();

         for (Entity entity : event.getClient().world.getEntities()) {
            if (entity instanceof PlayerEntity) {
               PlayerEntity player = (PlayerEntity)entity;
               if (player != event.getClient().player && player.isAlive() && player.isUsingItem()) {
                  ItemStack active = player.getActiveItem();
                  if (this.isTracked(active)) {
                     stillUsing.add(player.getId());
                     if (player.getItemUseTimeLeft() <= 1 && this.announcedUses.add(player.getId())) {
                        ChatUtil.info(player.getName().getString() + " used " + active.getName().getString());
                     }
                  }
               }
            }
         }

         this.announcedUses.retainAll(stillUsing);
      } else {
         this.announcedUses.clear();
      }
   }

   @EventTarget
   public void onPacket(PacketReceiveEvent event) {
      if (event.getPhase() == PacketReceiveEvent.Phase.PRE
         && this.tracked.isSelected("Totems")
         && event.getPacket() instanceof EntityStatusS2CPacket packet
         && packet.getStatus() == 35) {
         mc.execute(() -> {
            if (this.isEnabled() && mc.world != null) {
               if (packet.getEntity(mc.world) instanceof AbstractClientPlayerEntity player) {
                  ChatUtil.info(player == mc.player ? "You used a totem" : player.getName().getString() + " used a totem");
               }
            }
         });
      }
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.announcedUses.clear();
   }

   private boolean isTracked(ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return false;
      } else {
         return stack.getItem() instanceof PotionItem
            ? this.tracked.isSelected("Potions")
            : this.tracked.isSelected("Items") && (stack.contains(DataComponentTypes.FOOD) || stack.isOf(Items.MILK_BUCKET));
      }
   }
}
