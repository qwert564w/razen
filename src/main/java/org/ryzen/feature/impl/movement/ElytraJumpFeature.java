package org.ryzen.feature.impl.movement;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.utils.inventory.InventoryUtil;

@Environment(EnvType.CLIENT)
public final class ElytraJumpFeature extends Feature implements MinecraftContext {
   private static final List<Item> CHESTPLATES = List.of(
      Items.NETHERITE_CHESTPLATE,
      Items.DIAMOND_CHESTPLATE,
      Items.IRON_CHESTPLATE,
      Items.GOLDEN_CHESTPLATE,
      Items.CHAINMAIL_CHESTPLATE,
      Items.LEATHER_CHESTPLATE
   );
   private static final int CHEST_MENU_SLOT = 6;
   private static final double BOOST_STEP = 0.03062;
   public final BooleanSetting autoSwap = this.register(new BooleanSetting("Auto Swap", false));

   public ElytraJumpFeature() {
      super("ElytraJump", "Auto-deploys and holds an elytra glide", FeatureCategory.MOVEMENT, -1);
   }

   @Override
   protected void onEnable() {
      if (this.autoSwap.getValue() && mc.player != null) {
         this.equipElytra();
      }
   }

   @Override
   protected void onDisable() {
      ClientPlayerEntity player = mc.player;
      if (player != null) {
         mc.options.jumpKey.setPressed(false);
         if (this.autoSwap.getValue() && this.hasElytra(player)) {
            this.equipChestplate();
         }
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      ClientPlayerEntity player = event.getClient().player;
      if (player != null) {
         boolean grounded = player.isOnGround();
         boolean inFluid = player.isTouchingWater() || player.isInLava();
         if (!player.getAbilities().flying && grounded && !inFluid && this.hasElytra(player) && !mc.options.jumpKey.isPressed()) {
            player.jump();
         }

         if (!player.getAbilities().flying && !grounded && !inFluid && !player.isGliding() && this.canDeploy(player)) {
            this.deploy(player);
         }

         if (grounded || inFluid) {
            mc.options.jumpKey.setPressed(false);
         }

         if (player.hurtTime > 0 && this.autoSwap.getValue() && this.hasElytra(player)) {
            this.equipChestplate();
         } else {
            if (this.hasElytra(player)) {
               mc.options.jumpKey.setPressed(true);
               if (player.isGliding()) {
                  this.boost(player);
               }
            } else if (this.autoSwap.getValue()) {
               this.equipElytra();
            } else {
               mc.options.jumpKey.setPressed(false);
            }
         }
      }
   }

   private void boost(ClientPlayerEntity player) {
      Vec3d velocity = player.getVelocity();
      boolean rising = velocity.y > 0.08 || player.fallDistance > 0.1F;
      boolean still = Math.abs(velocity.x) <= 0.01 && Math.abs(velocity.z) <= 0.01;
      if (rising && still) {
         player.setVelocity(0.0, velocity.y + 0.03062, 0.0);
         player.knockedBack = true;
      }
   }

   private void deploy(ClientPlayerEntity player) {
      if (player.networkHandler != null) {
         player.networkHandler.sendPacket(new ClientCommandC2SPacket(player, Mode.START_FALL_FLYING));
         player.checkGliding();
      }
   }

   private boolean canDeploy(ClientPlayerEntity player) {
      if (this.hasElytra(player) && !player.isTouchingWater() && !player.isInLava() && !player.hasVehicle()) {
         ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
         return chest.isOf(Items.ELYTRA) && chest.getDamage() < chest.getMaxDamage() - 1;
      } else {
         return false;
      }
   }

   private boolean hasElytra(ClientPlayerEntity player) {
      return player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
   }

   private void equipElytra() {
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.interactionManager != null) {
         int slot = InventoryUtil.findPlayerMenuSlot(player, stack -> stack.isOf(Items.ELYTRA));
         if (slot != -1) {
            this.swapToChest(player, slot);
         }
      }
   }

   private void equipChestplate() {
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.interactionManager != null) {
         int slot = InventoryUtil.findPlayerMenuSlot(player, stack -> CHESTPLATES.contains(stack.getItem()));
         if (slot != -1) {
            this.swapToChest(player, slot);
         }
      }
   }

   private void swapToChest(ClientPlayerEntity player, int slot) {
      InventoryUtil.leftClickSlot(slot);
      InventoryUtil.leftClickSlot(6);
      InventoryUtil.leftClickSlot(slot);
   }
}
