package org.ryzen.feature.impl.player;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import org.ryzen.context.PlayerContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.packet.PacketSendEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.MultiSelectSetting;
import org.ryzen.utils.text.ChatUtil;

@Environment(EnvType.CLIENT)
public final class LockSlotFeature extends Feature implements PlayerContext {
   private static final String[] SLOT_OPTIONS = new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9"};
   private static final double PVP_RANGE = 16.0;
   public final BooleanSetting pvpOnly = this.register(new BooleanSetting("Only In PvP", true));
   public final MultiSelectSetting slots = this.register(new MultiSelectSetting("Locked Slots", List.of(), SLOT_OPTIONS));
   private long lastWarningAt;

   public LockSlotFeature() {
      super("LockSlot", "Blocks dropping items out of the chosen hotbar slots", FeatureCategory.PLAYER, -1);
   }

   public static LockSlotFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(LockSlotFeature.class);
   }

   @EventTarget
   public void onPacketSend(PacketSendEvent event) {
      if (event.getPhase() == PacketSendEvent.Phase.PRE && event.getPacket() instanceof PlayerActionC2SPacket packet) {
         if (packet.getAction() == Action.DROP_ITEM || packet.getAction() == Action.DROP_ALL_ITEMS) {
            ClientPlayerEntity player = this.localPlayer();
            if (player != null && !player.getMainHandStack().isEmpty()) {
               if (!this.pvpOnly.getValue() || this.hasNearbyPlayer(player)) {
                  int slot = player.getInventory().getSelectedSlot();
                  if (slot >= 0 && slot < SLOT_OPTIONS.length && this.slots.isSelected(SLOT_OPTIONS[slot])) {
                     event.cancel();
                     long now = System.currentTimeMillis();
                     if (now - this.lastWarningAt > 1500L) {
                        this.lastWarningAt = now;
                        ChatUtil.info("LockSlot: выброс из слота " + (slot + 1) + " заблокирован");
                     }
                  }
               }
            }
         }
      }
   }

   private boolean hasNearbyPlayer(ClientPlayerEntity player) {
      if (this.level() == null) {
         return false;
      } else {
         double rangeSqr = 256.0;

         for (AbstractClientPlayerEntity other : this.level().getPlayers()) {
            if (other != player && other.isAlive() && other.squaredDistanceTo(player) <= rangeSqr) {
               return true;
            }
         }

         return false;
      }
   }
}
