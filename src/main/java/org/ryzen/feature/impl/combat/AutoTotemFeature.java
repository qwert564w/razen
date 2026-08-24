package org.ryzen.feature.impl.combat;

import java.util.EnumSet;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.MultiSelectSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.pve.AutomationOwner;
import org.ryzen.pve.AutomationPriority;
import org.ryzen.pve.AutomationResource;
import org.ryzen.pve.PveAutomationCoordinator;
import org.ryzen.utils.inventory.InventorySwap;
import org.ryzen.utils.inventory.InventoryUtil;

@Environment(EnvType.CLIENT)
public final class AutoTotemFeature extends Feature implements MinecraftContext, AutomationOwner {
   private static final double DANGER_RADIUS = 6.0;
   private static final int ANCHOR_RADIUS_XZ = 4;
   private static final int ANCHOR_RADIUS_Y = 2;
   private static final double FALL_CLIP_DEPTH = 128.0;
   private static final double SAFE_FALL_DISTANCE = 3.0;
   public final NumberSetting health = this.register(new NumberSetting("Health", 16.0, 1.0, 20.0, 0.5, ""));
   public final BooleanSetting skipTalismans = this.register(new BooleanSetting("Skip Talismans", true));
   public final BooleanSetting notWhileEating = this.register(new BooleanSetting("Not While Eating", true));
   public final BooleanSetting notWithHead = this.register(new BooleanSetting("Not With Head", true));
   public final MultiSelectSetting dangers = this.register(
      new MultiSelectSetting(
         "Dangers",
         Set.of("Fall", "Crystal", "Explosion", "Obsidian", "Anchor", "Mace", "Spear"),
         "Fall",
         "Crystal",
         "Explosion",
         "Obsidian",
         "Anchor",
         "Mace",
         "Spear"
      )
   );
   private boolean releasePending;

   public AutoTotemFeature() {
      super("AutoTotem", "Keeps a totem of undying in your offhand", FeatureCategory.COMBAT, -1);
   }

   @Override
   protected void onDisable() {
      this.releasePending = false;
      PveAutomationCoordinator.INSTANCE.release(this);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      if (this.releasePending && !InventorySwap.isBusy()) {
         this.releasePending = false;
         PveAutomationCoordinator.INSTANCE.release(this);
      }

      ClientPlayerEntity player = this.player();
      if (player != null && !player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING) && !InventorySwap.isBusy()) {
         if (!this.notWhileEating.getValue() || !isEating(player)) {
            if (!this.notWithHead.getValue() || !player.getMainHandStack().isOf(Items.PLAYER_HEAD)) {
               if (!((double)player.getHealth() > this.health.getValue()) || this.dangerNearby(player)) {
                  int containerSlot = this.findTotemSlot(player);
                  if (containerSlot != -1
                     && PveAutomationCoordinator.INSTANCE.acquire(this, AutomationPriority.EMERGENCY, EnumSet.of(AutomationResource.INVENTORY))) {
                     InventorySwap.equip(containerSlot);
                     this.releasePending = true;
                  }
               }
            }
         }
      }
   }

   private static boolean isEating(ClientPlayerEntity player) {
      return player.isUsingItem() && player.getActiveItem().contains(DataComponentTypes.FOOD);
   }

   private boolean dangerNearby(ClientPlayerEntity player) {
      Box box = player.getBoundingBox().expand(6.0);
      return this.dangers.isSelected("Fall") && this.lethalFall(player)
         || this.dangers.isSelected("Crystal") && !mc.world.getNonSpectatingEntities(EndCrystalEntity.class, box).isEmpty()
         || this.dangers.isSelected("Explosion") && this.explosionNearby(box)
         || this.dangers.isSelected("Anchor") && this.anchorNearby(player)
         || this.armedPlayerNearby(player, box);
   }

   private boolean lethalFall(ClientPlayerEntity player) {
      if (!player.isOnGround()
         && !player.isTouchingWater()
         && !player.getAbilities().flying
         && !player.isGliding()
         && !player.hasStatusEffect(StatusEffects.SLOW_FALLING)
         && !(player.getVelocity().y >= 0.0)) {
         Vec3d from = player.getEntityPos();
         BlockHitResult hit = mc.world.raycast(new RaycastContext(from, from.add(0.0, -128.0, 0.0), ShapeType.COLLIDER, FluidHandling.ANY, player));
         if (hit.getType() != Type.MISS && !mc.world.getFluidState(hit.getBlockPos()).isEmpty()) {
            return false;
         } else {
            double groundY = hit.getType() == Type.MISS ? from.y - 128.0 : hit.getPos().y;
            double predictedDamage = player.fallDistance + (from.y - groundY) - 3.0;
            return predictedDamage >= (double)player.getHealth();
         }
      } else {
         return false;
      }
   }

   private boolean explosionNearby(Box box) {
      return !mc.world.getNonSpectatingEntities(TntEntity.class, box).isEmpty()
         ? true
         : !mc.world.getEntitiesByClass(CreeperEntity.class, box, creeper -> creeper.isIgnited() || creeper.getFuseSpeed() > 0).isEmpty();
   }

   private boolean anchorNearby(ClientPlayerEntity player) {
      BlockPos center = player.getBlockPos();

      for (BlockPos pos : BlockPos.iterate(center.add(-4, -2, -4), center.add(4, 2, 4))) {
         if (mc.world.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR)) {
            return true;
         }
      }

      return false;
   }

   private boolean armedPlayerNearby(ClientPlayerEntity player, Box box) {
      boolean obsidian = this.dangers.isSelected("Obsidian");
      boolean mace = this.dangers.isSelected("Mace");
      boolean spear = this.dangers.isSelected("Spear");
      if (!obsidian && !mace && !spear) {
         return false;
      } else {
         for (PlayerEntity other : mc.world.getEntitiesByClass(PlayerEntity.class, box, p -> p != player)) {
            if (isThreatItem(other.getMainHandStack(), obsidian, mace, spear) || isThreatItem(other.getOffHandStack(), obsidian, mace, spear)) {
               return true;
            }
         }

         return false;
      }
   }

   private static boolean isThreatItem(ItemStack held, boolean obsidian, boolean mace, boolean spear) {
      return obsidian && (held.isOf(Items.OBSIDIAN) || held.isOf(Items.CRYING_OBSIDIAN))
         || mace && held.isOf(Items.MACE)
         || spear && held.isIn(ItemTags.SPEARS);
   }

   private int findTotemSlot(ClientPlayerEntity player) {
      return InventoryUtil.findPlayerMenuSlot(player, this::isUsableTotem);
   }

   private boolean isUsableTotem(ItemStack stack) {
      return stack.isOf(Items.TOTEM_OF_UNDYING) && (!this.skipTalismans.getValue() || !stack.hasEnchantments());
   }
}
