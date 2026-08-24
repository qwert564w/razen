package org.ryzen.event.events.game;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.ryzen.event.Event;

@Environment(EnvType.CLIENT)
public final class PlayerJumpEvent extends Event {
   private ClientPlayerEntity player;
   private Vec3d position;

   public PlayerJumpEvent set(ClientPlayerEntity player, Vec3d position) {
      this.player = player;
      this.position = position;
      return this;
   }
   public ClientPlayerEntity getPlayer() {
      return this.player;
   }
   public Vec3d getPosition() {
      return this.position;
   }
}
