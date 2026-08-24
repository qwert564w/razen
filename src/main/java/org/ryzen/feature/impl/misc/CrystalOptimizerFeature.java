package org.ryzen.feature.impl.misc;

import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.ryzen.context.PlayerContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.mixin.accessor.MinecraftAccessor;

@Environment(EnvType.CLIENT)
public final class CrystalOptimizerFeature extends Feature implements PlayerContext {
   private static final Set<Item> VALUABLES = Set.of(
      Items.NETHERITE_HELMET,
      Items.NETHERITE_CHESTPLATE,
      Items.NETHERITE_LEGGINGS,
      Items.NETHERITE_BOOTS,
      Items.DIAMOND_HELMET,
      Items.DIAMOND_CHESTPLATE,
      Items.DIAMOND_LEGGINGS,
      Items.DIAMOND_BOOTS,
      Items.NETHERITE_SWORD,
      Items.DIAMOND_SWORD,
      Items.NETHERITE_PICKAXE,
      Items.DIAMOND_PICKAXE,
      Items.NETHERITE_SHOVEL,
      Items.DIAMOND_SHOVEL,
      Items.NETHERITE_AXE,
      Items.DIAMOND_AXE,
      Items.TOTEM_OF_UNDYING,
      Items.END_CRYSTAL,
      Items.ENCHANTED_GOLDEN_APPLE,
      Items.GOLDEN_APPLE,
      Items.ENDER_PEARL,
      Items.TRIDENT,
      Items.CROSSBOW,
      Items.ELYTRA
   );
   public final BooleanSetting protectDrops = this.register(new BooleanSetting("Protect Drops", true));
   public final NumberSetting range = this.register(new NumberSetting("Range", 3.0, 1.0, 6.0, 0.1, " blocks"));
   private EndCrystalEntity current;

   public CrystalOptimizerFeature() {
      super("CrystalOptimizer", "Breaks crystals without the use cooldown while right click is held", FeatureCategory.MISC, -1);
   }

   @Override
   protected void onDisable() {
      this.current = null;
   }

   public EndCrystalEntity getCurrent() {
      return this.isEnabled() ? this.current : null;
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      MinecraftClient client = event.getClient();
      ClientPlayerEntity player = client.player;
      if (player != null && client.world != null && client.interactionManager != null) {
         this.current = this.findCrystal(client, player, client.world);
         if (this.current != null && client.options.useKey.isPressed()) {
            ((MinecraftAccessor)client).setRightClickDelay(0);
            client.interactionManager.attackEntity(player, this.current);
            player.swingHand(Hand.MAIN_HAND);
         }
      } else {
         this.current = null;
      }
   }

   private EndCrystalEntity findCrystal(MinecraftClient client, ClientPlayerEntity player, ClientWorld level) {
      double reach = this.range.getValue();

      for (Entity entity : level.getEntities()) {
         if (entity instanceof EndCrystalEntity crystal
            && !((double)player.distanceTo(crystal) > reach)
            && isAimedAt(client, player, level, crystal, reach)
            && (!this.protectDrops.getValue() || !hasValuablesNearby(level, crystal.getBlockPos()))) {
            return crystal;
         }
      }

      return null;
   }

   private static boolean isAimedAt(MinecraftClient client, ClientPlayerEntity player, ClientWorld level, EndCrystalEntity crystal, double reach) {
      Vec3d eye = player.getEyePos();
      Vec3d end = eye.add(player.getRotationVec(1.0F).multiply(reach));
      EntityHitResult entityHit = ProjectileUtil.getEntityCollision(
         level, player, eye, end, new Box(eye, end).expand(1.0), candidate -> candidate == crystal, 0.0F
      );
      if (entityHit != null) {
         return true;
      } else {
         BlockHitResult blockHit = level.raycast(new RaycastContext(eye, end, ShapeType.OUTLINE, FluidHandling.NONE, player));
         return blockHit.getType() == Type.BLOCK && blockHit.getBlockPos().equals(crystal.getBlockPos().down());
      }
   }

   private static boolean hasValuablesNearby(ClientWorld level, BlockPos pos) {
      Box box = new Box(
         (double)pos.getX() - 3.0,
         (double)pos.getY() - 3.0,
         (double)pos.getZ() - 3.0,
         (double)pos.getX() + 4.0,
         (double)pos.getY() + 5.0,
         (double)pos.getZ() + 4.0
      );

      for (ItemEntity item : level.getNonSpectatingEntities(ItemEntity.class, box)) {
         if (VALUABLES.contains(item.getStack().getItem())) {
            return true;
         }
      }

      return false;
   }
}
