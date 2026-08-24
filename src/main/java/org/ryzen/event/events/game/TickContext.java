package org.ryzen.event.events.game;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;

@Environment(EnvType.CLIENT)
public final class TickContext {
   private MinecraftClient client;
   private ClientPlayerEntity player;
   private ClientWorld level;
   private boolean worldReady;
   private boolean closestAttackableResolved;
   private Entity closestAttackableTarget;
   private double closestAttackableDistance = Double.POSITIVE_INFINITY;

   public TickContext begin(MinecraftClient client) {
      this.client = client;
      this.player = client.player;
      this.level = client.world;
      this.worldReady = this.player != null && this.level != null;
      this.closestAttackableResolved = false;
      this.closestAttackableTarget = null;
      this.closestAttackableDistance = Double.POSITIVE_INFINITY;
      return this;
   }
   public MinecraftClient getClient() {
      return this.client;
   }
   public ClientPlayerEntity getPlayer() {
      return this.player;
   }
   public ClientWorld getLevel() {
      return this.level;
   }
   public boolean isWorldReady() {
      return this.worldReady;
   }
   public boolean isClosestAttackableResolved() {
      return this.closestAttackableResolved;
   }
   public Entity getClosestAttackableTarget() {
      return this.closestAttackableTarget;
   }
   public double getClosestAttackableDistance() {
      return this.closestAttackableDistance;
   }
}
