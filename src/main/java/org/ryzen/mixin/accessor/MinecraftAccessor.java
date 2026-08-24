package org.ryzen.mixin.accessor;

import com.mojang.authlib.yggdrasil.ProfileResult;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin({MinecraftClient.class})
public interface MinecraftAccessor {
   @Accessor("itemUseCooldown")
   void setRightClickDelay(int var1);

   @Accessor("session")
   @Mutable
   void setUser(Session var1);

   @Accessor("gameProfileFuture")
   @Mutable
   void setProfileFuture(CompletableFuture<ProfileResult> var1);
}
