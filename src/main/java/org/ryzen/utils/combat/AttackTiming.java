package org.ryzen.utils.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public final class AttackTiming {
   private static final int[] PATTERN = new int[]{10, 10, 10, 13};
   private static final float READY_CHARGE = 0.9F;
   private int extraDelayTicks;
   private int hitCounter;

   public void tick() {
      if (this.extraDelayTicks > 0) {
         this.extraDelayTicks--;
      }
   }

   public void onSwingPacket(boolean usePattern, boolean tpsSync) {
      float pattern = usePattern ? (float)PATTERN[this.hitCounter % PATTERN.length] : 0.0F;
      float scale = 1.0F;
      if (tpsSync) {
         float tps = MathHelper.clamp(ServerTickSync.INSTANCE.effectiveTps(), 1.0F, 20.0F);
         scale = 20.0F / tps;
      }

      this.extraDelayTicks = Math.max(0, Math.round(pattern * scale));
   }

   public boolean cooldownReady(ClientPlayerEntity player, int ticksAhead) {
      int delayLeft = this.extraDelayTicks - ticksAhead;
      return player.getMainHandStack().isOf(Items.MACE) ? delayLeft <= 0 : player.getAttackCooldownProgress((float)ticksAhead + 0.5F) > 0.9F && delayLeft <= 0;
   }

   public void onAttack() {
      this.hitCounter++;
   }

   public int hitCounter() {
      return this.hitCounter;
   }

   public void reset() {
      this.extraDelayTicks = 0;
   }
}
