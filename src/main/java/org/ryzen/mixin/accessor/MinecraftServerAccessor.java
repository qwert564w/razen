package org.ryzen.mixin.accessor;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorage.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin({MinecraftServer.class})
public interface MinecraftServerAccessor {
   @Accessor("session")
   Session getStorageSource();
}
