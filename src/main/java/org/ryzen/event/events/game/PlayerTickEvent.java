package org.ryzen.event.events.game;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import org.ryzen.event.Event;

@Environment(EnvType.CLIENT)
public final class PlayerTickEvent extends Event {
   private ClientPlayerEntity player;
   private PlayerTickEvent.Phase phase;

   public PlayerTickEvent set(ClientPlayerEntity player, PlayerTickEvent.Phase phase) {
      this.player = player;
      this.phase = phase;
      return this;
   }

   public boolean isPre() {
      return this.phase == PlayerTickEvent.Phase.PRE;
   }

   public boolean isPost() {
      return this.phase == PlayerTickEvent.Phase.POST;
   }
   public ClientPlayerEntity getPlayer() {
      return this.player;
   }
   public PlayerTickEvent.Phase getPhase() {
      return this.phase;
   }

   @Environment(EnvType.CLIENT)
   public static enum Phase {
      PRE,
      POST;
   }
}
