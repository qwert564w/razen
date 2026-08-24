package org.ryzen.context;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;

@Environment(EnvType.CLIENT)
public interface WorldContext extends MinecraftContext {
   default ClientWorld world() {
      return this.level();
   }

   default boolean hasWorld() {
      return this.world() != null;
   }
}
