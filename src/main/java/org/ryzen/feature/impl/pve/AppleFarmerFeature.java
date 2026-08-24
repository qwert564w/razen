package org.ryzen.feature.impl.pve;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.game.GameTickEvent;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.feature.setting.TextSetting;
import org.ryzen.pve.AutomationPriority;
import org.ryzen.pve.AutomationResource;
import org.ryzen.pve.PveAutomationCoordinator;
import org.ryzen.pve.PveFeature;
import org.ryzen.pve.PveStateMachine;
import org.ryzen.pve.navigation.BaritoneNavigator;
import org.ryzen.pve.navigation.NavigationOptions;
import org.ryzen.pve.server.ServerAdapter;
import org.ryzen.pve.server.ServerAdapters;
import org.ryzen.utils.inventory.ContainerLootService;
import org.ryzen.utils.inventory.InventoryUtil;

@Environment(EnvType.CLIENT)
public final class AppleFarmerFeature extends PveFeature implements MinecraftContext {
   private static final int OFFHAND_SWAP_BUTTON = 40;
   private static final int CRAFT_RESULT_SLOT = 0;
   private static final int CRAFT_INPUT_SLOT = 1;
   private static final int HOME_SETTLE_TICKS = 60;
   private static final int ACTION_INTERVAL_TICKS = 4;
   private static final int STORAGE_RETRY_TICKS = 1200;
   private static final double INTERACT_DISTANCE_SQUARED = 20.25;
   public final BooleanSetting takeBones = this.register(new BooleanSetting("Take Bones", false));
   public final BooleanSetting dropJunk = this.register(new BooleanSetting("Drop Junk", false));
   public final TextSetting farmPosition = this.register(new TextSetting("Farm Position", "auto"));
   public final TextSetting appleStorage = this.register(new TextSetting("Apple Storage", "auto"));
   public final TextSetting boneStorage = this.register(new TextSetting("Bone Storage", "auto"));
   public final BooleanSetting useHomeCommand = this.register(new BooleanSetting("Use Home Command", false));
   public final NumberSetting farmRadius = this.register(new NumberSetting("Farm Radius", 8.0, 3.0, 24.0, 1.0, " blocks"));
   public final NumberSetting verticalScan = this.register(new NumberSetting("Vertical Scan", 10.0, 3.0, 24.0, 1.0, " blocks"));
   public final NumberSetting treesPerCycle = this.register(new NumberSetting("Trees Per Cycle", 4.0, 1.0, 32.0, 1.0, ""));
   public final NumberSetting maxBlocksPerCycle = this.register(new NumberSetting("Max Blocks Per Cycle", 192.0, 16.0, 1024.0, 16.0, ""));
   public final NumberSetting boneMealPerCycle = this.register(new NumberSetting("Bone Meal Per Cycle", 48.0, 1.0, 256.0, 1.0, ""));
   public final NumberSetting boneMealReserve = this.register(new NumberSetting("Bone Meal Reserve", 16.0, 0.0, 256.0, 1.0, ""));
   public final NumberSetting saplingReserve = this.register(new NumberSetting("Sapling Reserve", 16.0, 1.0, 128.0, 1.0, ""));
   public final NumberSetting depositAppleStacks = this.register(new NumberSetting("Deposit Apple Stacks", 4.0, 1.0, 27.0, 1.0, " stacks"));
   public final NumberSetting repairBelow = this.register(new NumberSetting("Repair Below", 20.0, 1.0, 95.0, 1.0, "%"));
   public final NumberSetting repairTo = this.register(new NumberSetting("Repair To", 90.0, 5.0, 100.0, 1.0, "%"));
   public final BooleanSetting buyXpBottles = this.register(new BooleanSetting("Buy XP Bottles", false));
   public final TextSetting xpBottleBuyCommand = this.register(new TextSetting("XP Bottle Buy Command", ""));
   private final PveStateMachine<AppleFarmerFeature.Phase> state = new PveStateMachine<>(AppleFarmerFeature.Phase.RETURN_HOME);
   private final BaritoneNavigator navigator = BaritoneNavigator.INSTANCE;
   private long tick;
   private long lastActionTick;
   private long lastNavigationTick;
   private long skipAppleStorageUntil;
   private long skipBoneStorageUntil;
   private BlockPos farmCenter;
   private BlockPos appleChest;
   private BlockPos boneChest;
   private BlockPos activeWorkPos;
   private boolean navigationActive;
   private boolean miningIssued;
   private boolean homeCommandSent;
   private boolean buyCommandSent;
   private boolean openedContainer;
   private boolean storageOpenRequested;
   private int plantedThisCycle;
   private int boneMealUsedThisCycle;
   private int blocksBrokenThisCycle;
   private int completedCycles;
   private int craftStep;
   private int craftSourceSlot = -1;
   private long craftStepTick;
   private int repairToolSourceSlot = -1;
   private float savedYaw;
   private float savedPitch;
   private boolean rotationSaved;

   public AppleFarmerFeature() {
      super(
         "AppleFarmer",
         "Plants, grows and harvests apple trees with storage and repair cycles",
         -1,
         AutomationPriority.BOT,
         AutomationResource.MOVEMENT,
         AutomationResource.ROTATION,
         AutomationResource.INVENTORY,
         AutomationResource.SCREEN,
         AutomationResource.CHAT,
         AutomationResource.NAVIGATION
      );
   }

   @Override
   protected void onPveEnable() {
      this.tick = 0L;
      this.lastActionTick = -4611686018427387904L;
      this.lastNavigationTick = -4611686018427387904L;
      this.skipAppleStorageUntil = 0L;
      this.skipBoneStorageUntil = 0L;
      this.farmCenter = null;
      this.appleChest = null;
      this.boneChest = null;
      this.activeWorkPos = null;
      this.navigationActive = false;
      this.miningIssued = false;
      this.homeCommandSent = false;
      this.buyCommandSent = false;
      this.openedContainer = false;
      this.storageOpenRequested = false;
      this.plantedThisCycle = 0;
      this.boneMealUsedThisCycle = 0;
      this.blocksBrokenThisCycle = 0;
      this.completedCycles = 0;
      this.craftStep = 0;
      this.craftSourceSlot = -1;
      this.repairToolSourceSlot = -1;
      this.rotationSaved = false;
      this.state.reset(0L);
      this.beginNavigation(NavigationOptions.walking());
   }

   @Override
   protected void onPveDisable() {
      ClientPlayerEntity player = mc.player;
      this.restoreCraftingInventory(player);
      this.restoreRepairTool(player);
      this.closeFeatureContainer(player);
      this.restoreRotation(player);
      this.endNavigation();
      this.activeWorkPos = null;
      this.miningIssued = false;
   }

   @Override
   protected void onPvePreempted(PveAutomationCoordinator.RevocationReason reason) {
      this.cancelNavigation();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      ClientPlayerEntity player = event.getClient().player;
      ClientWorld level = event.getClient().world;
      if (player != null && level != null && event.getClient().interactionManager != null && player.isAlive()) {
         this.tick++;
         switch ((AppleFarmerFeature.Phase)this.state.state()) {
            case RETURN_HOME:
               this.tickReturnHome(player);
               break;
            case FIND_FARM:
               this.tickFindFarm(player);
               break;
            case APPROACH_FARM:
               this.tickApproachFarm(player);
               break;
            case PLANT:
               this.tickPlant(player, level);
               break;
            case GROW:
               this.tickGrow(player, level);
               break;
            case BREAK_LEAVES:
               this.tickBreakBlocks(player, level, true);
               break;
            case BREAK_LOGS:
               this.tickBreakBlocks(player, level, false);
               break;
            case DROP_JUNK:
               this.tickDropJunk(player);
               break;
            case DEPOSIT_APPLES_FIND:
               this.tickFindStorage(player, level, AppleFarmerFeature.StorageKind.APPLES);
               break;
            case DEPOSIT_APPLES_MOVE:
               this.tickMoveToStorage(player, this.appleChest, AppleFarmerFeature.Phase.DEPOSIT_APPLES_OPEN);
               break;
            case DEPOSIT_APPLES_OPEN:
               this.tickDepositApples(player);
               break;
            case BONES_FIND:
               this.tickFindStorage(player, level, AppleFarmerFeature.StorageKind.BONES);
               break;
            case BONES_MOVE:
               this.tickMoveToStorage(player, this.boneChest, AppleFarmerFeature.Phase.BONES_OPEN);
               break;
            case BONES_OPEN:
               this.tickTakeBones(player);
               break;
            case CRAFT_BONE_MEAL:
               this.tickCraftBoneMeal(player);
               break;
            case BUY_BOTTLES:
               this.tickBuyBottles(player);
               break;
            case REPAIR:
               this.tickRepair(player);
         }
      }
   }

   public AppleFarmerFeature.Phase getPhase() {
      return this.state.state();
   }

   public int getCompletedCycles() {
      return this.completedCycles;
   }

   private void tickReturnHome(ClientPlayerEntity player) {
      if (!this.useHomeCommand.getValue()) {
         this.transition(AppleFarmerFeature.Phase.FIND_FARM);
      } else {
         if (!this.homeCommandSent) {
            ServerAdapter adapter = ServerAdapters.current();
            Optional<String> command = adapter.homeCommand(PveManagerFeature.INSTANCE.resolvedHomeName());
            if (command.isEmpty()) {
               this.transition(AppleFarmerFeature.Phase.FIND_FARM);
               return;
            }

            adapter.sendCommand(player, command.get());
            this.homeCommandSent = true;
            this.lastActionTick = this.tick;
         }

         if (this.state.ticksInState(this.tick) >= 60L) {
            if (PveCoordinateParser.parse(this.farmPosition.getValue()).isEmpty()) {
               this.farmCenter = player.getBlockPos().toImmutable();
            }

            this.transition(AppleFarmerFeature.Phase.FIND_FARM);
         }
      }
   }

   private void tickFindFarm(ClientPlayerEntity player) {
      this.farmCenter = PveCoordinateParser.parse(this.farmPosition.getValue())
         .orElseGet(() -> this.farmCenter == null ? player.getBlockPos().toImmutable() : this.farmCenter);
      AppleFarmerFeature.FarmerSignals signals = signals(
         at(player, this.farmCenter, 3.0), false, false, false, false, false, false, false, false, false, false, false, false, false
      );
      this.transition(nextPhase(AppleFarmerFeature.Phase.FIND_FARM, signals));
   }

   private void tickApproachFarm(ClientPlayerEntity player) {
      if (this.farmCenter == null) {
         this.transition(AppleFarmerFeature.Phase.FIND_FARM);
      } else {
         boolean atFarm = at(player, this.farmCenter, 3.0);
         boolean timedOut = this.state.ticksInState(this.tick) > 1200L;
         if (!atFarm && !timedOut) {
            this.navigateTo(this.farmCenter, 2, NavigationOptions.walking());
         }

         this.transition(
            nextPhase(
               AppleFarmerFeature.Phase.APPROACH_FARM,
               signals(atFarm, false, false, false, false, false, false, false, false, false, false, false, false, timedOut)
            )
         );
      }
   }

   private void tickPlant(ClientPlayerEntity player, ClientWorld level) {
      if (this.farmCenter == null) {
         this.transition(AppleFarmerFeature.Phase.FIND_FARM);
      } else {
         int limit = intValue(this.treesPerCycle);
         if (this.plantedThisCycle >= limit) {
            this.transition(AppleFarmerFeature.Phase.GROW);
         } else {
            BlockPos target = this.findPlantingTarget(level);
            boolean hasSapling = countItem(player, Items.OAK_SAPLING) > 0;
            boolean timedOut = this.state.ticksInState(this.tick) > 600L;
            AppleFarmerFeature.Phase next = nextPhase(
               AppleFarmerFeature.Phase.PLANT,
               signals(true, target != null, hasSapling, false, false, false, false, false, false, false, false, false, false, timedOut)
            );
            if (next != AppleFarmerFeature.Phase.PLANT) {
               this.transition(next);
            } else if (!at(player, target, 4.0)) {
               this.navigateTo(target, 2, NavigationOptions.walking());
            } else if (this.actionReady() && this.selectHotbarItem(player, Items.OAK_SAPLING)) {
               this.cancelNavigation();
               this.lookAt(player, Vec3d.ofCenter(target.up()));
               BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(target).add(0.0, 0.5, 0.0), Direction.UP, target, false);
               mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
               player.swingHand(Hand.MAIN_HAND);
               this.lastActionTick = this.tick;
               this.plantedThisCycle++;
            }
         }
      }
   }

   private void tickGrow(ClientPlayerEntity player, ClientWorld level) {
      if (this.farmCenter == null) {
         this.transition(AppleFarmerFeature.Phase.FIND_FARM);
      } else {
         BlockPos sapling = this.findNearestBlock(level, player, true, Blocks.OAK_SAPLING);
         boolean hasBoneMeal = countItem(player, Items.BONE_MEAL) > 0;
         boolean underLimit = this.boneMealUsedThisCycle < intValue(this.boneMealPerCycle);
         boolean timedOut = this.state.ticksInState(this.tick) > 900L;
         AppleFarmerFeature.Phase next = nextPhase(
            AppleFarmerFeature.Phase.GROW,
            signals(true, sapling != null && underLimit, hasBoneMeal, false, false, false, false, false, false, false, false, false, false, timedOut)
         );
         if (next != AppleFarmerFeature.Phase.GROW) {
            this.transition(next);
         } else if (!at(player, sapling, 4.0)) {
            this.navigateTo(sapling, 2, NavigationOptions.walking());
         } else if (this.actionReady() && this.selectHotbarItem(player, Items.BONE_MEAL)) {
            this.cancelNavigation();
            this.lookAt(player, Vec3d.ofCenter(sapling));
            BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(sapling), Direction.UP, sapling, false);
            mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
            player.swingHand(Hand.MAIN_HAND);
            this.lastActionTick = this.tick;
            this.boneMealUsedThisCycle++;
         }
      }
   }

   private void tickBreakBlocks(ClientPlayerEntity player, ClientWorld level, boolean leaves) {
      Block[] targets = leaves ? new Block[]{Blocks.OAK_LEAVES} : new Block[]{Blocks.OAK_LOG, Blocks.OAK_WOOD};
      if (this.blocksBrokenThisCycle >= intValue(this.maxBlocksPerCycle)) {
         this.transition(leaves ? AppleFarmerFeature.Phase.BREAK_LOGS : AppleFarmerFeature.Phase.DROP_JUNK);
      } else {
         if (this.activeWorkPos != null && !matchesAny(level.getBlockState(this.activeWorkPos), targets)) {
            this.blocksBrokenThisCycle++;
            this.activeWorkPos = null;
            this.miningIssued = false;
            this.lastActionTick = this.tick;
         }

         if (this.activeWorkPos == null) {
            this.activeWorkPos = this.findNearestBlock(level, player, false, targets);
         }

         boolean timedOut = this.state.ticksInState(this.tick) > 1800L;
         AppleFarmerFeature.Phase current = leaves ? AppleFarmerFeature.Phase.BREAK_LEAVES : AppleFarmerFeature.Phase.BREAK_LOGS;
         AppleFarmerFeature.Phase next = nextPhase(
            current, signals(true, this.activeWorkPos != null, true, false, false, false, false, false, false, false, false, false, false, timedOut)
         );
         if (next != current) {
            this.transition(next);
         } else {
            if (!this.miningIssued || !this.navigator.isPathing() && this.tick - this.lastNavigationTick >= 40L) {
               if (!this.beginNavigation(NavigationOptions.mining())) {
                  this.transition(leaves ? AppleFarmerFeature.Phase.BREAK_LOGS : AppleFarmerFeature.Phase.DROP_JUNK);
                  return;
               }

               try {
                  this.navigator.mine(0, level.getBlockState(this.activeWorkPos).getBlock());
                  this.miningIssued = true;
                  this.lastNavigationTick = this.tick;
               } catch (LinkageError | RuntimeException var9) {
                  this.miningIssued = false;
               }
            }
         }
      }
   }

   private void tickDropJunk(ClientPlayerEntity player) {
      int junkSlot = this.dropJunk.getValue() ? this.findJunkSlot(player) : -1;
      boolean timedOut = this.state.ticksInState(this.tick) > 400L;
      if (junkSlot >= 0 && !timedOut) {
         if (this.actionReady()) {
            ItemStack stack = player.playerScreenHandler.getSlot(junkSlot).getStack();
            boolean oneSapling = stack.isOf(Items.OAK_SAPLING);
            this.clickPlayerMenu(player, junkSlot, oneSapling ? 0 : 1, SlotActionType.THROW);
            this.lastActionTick = this.tick;
         }
      } else {
         this.transition(nextPhase(AppleFarmerFeature.Phase.DROP_JUNK, this.maintenanceSignals(player, false, false, timedOut)));
      }
   }

   private void tickFindStorage(ClientPlayerEntity player, ClientWorld level, AppleFarmerFeature.StorageKind kind) {
      BlockPos found = this.resolveStorage(player, level, kind);
      if (kind == AppleFarmerFeature.StorageKind.APPLES) {
         this.appleChest = found;
      } else {
         this.boneChest = found;
      }

      boolean timedOut = this.state.ticksInState(this.tick) > 200L;
      AppleFarmerFeature.Phase current = kind == AppleFarmerFeature.StorageKind.APPLES
         ? AppleFarmerFeature.Phase.DEPOSIT_APPLES_FIND
         : AppleFarmerFeature.Phase.BONES_FIND;
      if (found != null) {
         this.transition(kind == AppleFarmerFeature.StorageKind.APPLES ? AppleFarmerFeature.Phase.DEPOSIT_APPLES_MOVE : AppleFarmerFeature.Phase.BONES_MOVE);
      } else if (timedOut) {
         if (kind == AppleFarmerFeature.StorageKind.APPLES) {
            this.skipAppleStorageUntil = this.tick + 1200L;
            this.transition(nextPhase(current, this.maintenanceSignals(player, true, false, true)));
         } else {
            this.skipBoneStorageUntil = this.tick + 1200L;
            this.transition(nextPhase(current, this.maintenanceSignals(player, false, true, true)));
         }
      }
   }

   private void tickMoveToStorage(ClientPlayerEntity player, BlockPos target, AppleFarmerFeature.Phase openPhase) {
      AppleFarmerFeature.Phase current = openPhase == AppleFarmerFeature.Phase.DEPOSIT_APPLES_OPEN
         ? AppleFarmerFeature.Phase.DEPOSIT_APPLES_MOVE
         : AppleFarmerFeature.Phase.BONES_MOVE;
      if (target == null) {
         this.transition(
            openPhase == AppleFarmerFeature.Phase.DEPOSIT_APPLES_OPEN ? AppleFarmerFeature.Phase.DEPOSIT_APPLES_FIND : AppleFarmerFeature.Phase.BONES_FIND
         );
      } else {
         boolean atDestination = at(player, target, 4.0);
         boolean timedOut = this.state.ticksInState(this.tick) > 800L;
         if (!atDestination && !timedOut) {
            this.navigateTo(target, 2, NavigationOptions.walking());
         } else if (timedOut) {
            if (current == AppleFarmerFeature.Phase.DEPOSIT_APPLES_MOVE) {
               this.skipAppleStorageUntil = this.tick + 1200L;
               this.transition(nextPhase(current, this.maintenanceSignals(player, true, false, true)));
            } else {
               this.skipBoneStorageUntil = this.tick + 1200L;
               this.transition(nextPhase(current, this.maintenanceSignals(player, false, true, true)));
            }
         } else {
            this.transition(openPhase);
         }
      }
   }

   private void tickDepositApples(ClientPlayerEntity player) {
      ScreenHandler menu = this.openStorageMenu(player, this.appleChest);
      if (menu == null) {
         if (this.state.ticksInState(this.tick) > 200L) {
            this.skipAppleStorageUntil = this.tick + 1200L;
            this.transition(nextPhase(AppleFarmerFeature.Phase.DEPOSIT_APPLES_OPEN, this.maintenanceSignals(player, true, false, true)));
         }
      } else if (this.state.ticksInState(this.tick) > 400L) {
         this.skipAppleStorageUntil = this.tick + 1200L;
         this.closeFeatureContainer(player);
         this.transition(nextPhase(AppleFarmerFeature.Phase.DEPOSIT_APPLES_OPEN, this.maintenanceSignals(player, true, false, true)));
      } else {
         int slot = findPlayerContainerSlot(menu, stack -> stack.isOf(Items.APPLE));
         if (slot >= 0 && this.actionReady()) {
            InventoryUtil.quickMoveSlot(slot);
            this.lastActionTick = this.tick;
         } else if (slot < 0) {
            this.closeFeatureContainer(player);
            this.transition(nextPhase(AppleFarmerFeature.Phase.DEPOSIT_APPLES_OPEN, this.maintenanceSignals(player, true, false, false)));
         }
      }
   }

   private void tickTakeBones(ClientPlayerEntity player) {
      ScreenHandler menu = this.openStorageMenu(player, this.boneChest);
      if (menu == null) {
         if (this.state.ticksInState(this.tick) > 200L) {
            this.skipBoneStorageUntil = this.tick + 1200L;
            this.transition(AppleFarmerFeature.Phase.CRAFT_BONE_MEAL);
         }
      } else if (this.state.ticksInState(this.tick) > 400L) {
         this.skipBoneStorageUntil = this.tick + 1200L;
         this.closeFeatureContainer(player);
         this.transition(AppleFarmerFeature.Phase.CRAFT_BONE_MEAL);
      } else {
         int targetBones = Math.max(1, intValue(this.boneMealReserve) / 3);
         int boneCount = countItem(player, Items.BONE);
         int slot = ContainerLootService.findFirst(menu, stack -> stack.isOf(Items.BONE));
         if (slot >= 0 && boneCount < targetBones && this.actionReady()) {
            ContainerLootService.quickMoveFirst(menu, stack -> stack.isOf(Items.BONE));
            this.lastActionTick = this.tick;
         } else {
            if (slot < 0 && boneCount < targetBones) {
               this.skipBoneStorageUntil = this.tick + 1200L;
            }

            this.closeFeatureContainer(player);
            this.transition(AppleFarmerFeature.Phase.CRAFT_BONE_MEAL);
         }
      }
   }

   private void tickCraftBoneMeal(ClientPlayerEntity player) {
      if (this.state.ticksInState(this.tick) > 400L) {
         this.restoreCraftingInventory(player);
         this.skipBoneStorageUntil = this.tick + 1200L;
         this.transition(AppleFarmerFeature.Phase.PLANT);
      } else {
         int reserve = intValue(this.boneMealReserve);
         if (countItem(player, Items.BONE_MEAL) >= reserve || countItem(player, Items.BONE) <= 0) {
            this.restoreCraftingInventory(player);
            this.transition(nextPhase(AppleFarmerFeature.Phase.CRAFT_BONE_MEAL, this.maintenanceSignals(player, false, false, false)));
         } else if (player.currentScreenHandler == player.playerScreenHandler && !this.openedContainer) {
            switch (this.craftStep) {
               case 0:
                  if (!player.playerScreenHandler.getCursorStack().isEmpty()) {
                     this.restoreCraftingInventory(player);
                     return;
                  }

                  this.craftSourceSlot = findPlayerSlot(player, Items.BONE);
                  if (this.craftSourceSlot < 0) {
                     this.transition(nextPhase(AppleFarmerFeature.Phase.CRAFT_BONE_MEAL, this.maintenanceSignals(player, false, true, false)));
                     return;
                  }

                  this.clickPlayerMenu(player, this.craftSourceSlot, 0, SlotActionType.PICKUP);
                  this.clickPlayerMenu(player, 1, 0, SlotActionType.PICKUP);
                  this.craftStep = 1;
                  this.craftStepTick = this.tick;
                  break;
               case 1:
                  ItemStack result = player.playerScreenHandler.getSlot(0).getStack();
                  if (result.isOf(Items.BONE_MEAL)) {
                     this.clickPlayerMenu(player, 0, 0, SlotActionType.QUICK_MOVE);
                     this.craftStep = 2;
                     this.craftStepTick = this.tick;
                  } else if (this.tick - this.craftStepTick > 20L) {
                     this.craftStep = 2;
                     this.craftStepTick = this.tick;
                  }
                  break;
               case 2:
                  if (this.tick - this.craftStepTick < 2L) {
                     return;
                  }

                  this.restoreCraftingInventory(player);
                  this.craftStep = 0;
                  this.craftSourceSlot = -1;
                  break;
               default:
                  this.restoreCraftingInventory(player);
                  this.craftStep = 0;
            }
         } else {
            this.closeFeatureContainer(player);
         }
      }
   }

   private void tickBuyBottles(ClientPlayerEntity player) {
      if (countItem(player, Items.EXPERIENCE_BOTTLE) > 0) {
         this.transition(AppleFarmerFeature.Phase.REPAIR);
      } else {
         String command = this.xpBottleBuyCommand.getValue().trim();
         if (this.buyXpBottles.getValue() && !command.isEmpty()) {
            if (!this.buyCommandSent) {
               ServerAdapters.current().sendCommand(player, command);
               this.buyCommandSent = true;
               this.lastActionTick = this.tick;
            }

            if (player.currentScreenHandler != player.playerScreenHandler) {
               this.openedContainer = true;
            }

            if (this.state.ticksInState(this.tick) > 160L) {
               this.closeFeatureContainer(player);
               this.transition(countItem(player, Items.EXPERIENCE_BOTTLE) > 0 ? AppleFarmerFeature.Phase.REPAIR : AppleFarmerFeature.Phase.PLANT);
            }
         } else {
            this.transition(AppleFarmerFeature.Phase.PLANT);
         }
      }
   }

   private void tickRepair(ClientPlayerEntity player) {
      if (this.repairToolSourceSlot < 0) {
         int toolSlot = findRepairToolSlot(player, this.repairBelow.getValue());
         if (toolSlot < 0) {
            this.transition(AppleFarmerFeature.Phase.PLANT);
         } else {
            this.saveRotation(player);
            this.swapWithOffhand(player, toolSlot);
            this.repairToolSourceSlot = toolSlot;
            this.lastActionTick = this.tick;
         }
      } else {
         ItemStack tool = player.getOffHandStack();
         boolean repaired = !needsRepair(tool, this.repairTo.getValue());
         int bottleSlot = findPlayerSlot(player, Items.EXPERIENCE_BOTTLE);
         boolean timedOut = this.state.ticksInState(this.tick) > 800L;
         if (repaired || bottleSlot < 0 || timedOut) {
            this.restoreRepairTool(player);
            this.transition(AppleFarmerFeature.Phase.PLANT);
         } else if (this.actionReady()) {
            this.lookAt(player, player.getEntityPos().add(0.0, -1.0, 0.0));
            this.useFromPlayerSlot(player, bottleSlot);
            this.lastActionTick = this.tick;
         }
      }
   }

   private ScreenHandler openStorageMenu(ClientPlayerEntity player, BlockPos storage) {
      if ((this.storageOpenRequested || this.openedContainer) && InventoryUtil.isContainerScreenOpen()) {
         ScreenHandler menu = InventoryUtil.getOpenMenu();
         if (menu != null && menu != player.playerScreenHandler) {
            this.openedContainer = true;
            return menu;
         }
      }

      if (storage != null && at(player, storage, 4.0) && this.actionReady()) {
         this.cancelNavigation();
         this.lookAt(player, Vec3d.ofCenter(storage));
         BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(storage), Direction.UP, storage, false);
         mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
         player.swingHand(Hand.MAIN_HAND);
         this.storageOpenRequested = true;
         this.lastActionTick = this.tick;
         return null;
      } else {
         return null;
      }
   }

   private void closeFeatureContainer(ClientPlayerEntity player) {
      if (this.openedContainer && player != null && player.currentScreenHandler != player.playerScreenHandler) {
         player.closeHandledScreen();
      }

      this.openedContainer = false;
      this.storageOpenRequested = false;
   }

   private BlockPos resolveStorage(ClientPlayerEntity player, ClientWorld level, AppleFarmerFeature.StorageKind kind) {
      String setting = kind == AppleFarmerFeature.StorageKind.APPLES ? this.appleStorage.getValue() : this.boneStorage.getValue();
      Optional<BlockPos> configured = PveCoordinateParser.parse(setting);
      if (configured.isPresent()) {
         return configured.get();
      } else {
         BlockPos origin = this.farmCenter == null ? player.getBlockPos() : this.farmCenter;
         BlockPos signed = this.findSignedChest(level, origin, kind);
         return signed != null ? signed : this.findNearestChest(level, player, origin);
      }
   }

   private BlockPos findSignedChest(ClientWorld level, BlockPos origin, AppleFarmerFeature.StorageKind kind) {
      int radius = Math.min(16, intValue(this.farmRadius) + 4);
      int yRadius = Math.min(8, intValue(this.verticalScan));

      for (BlockPos cursor : BlockPos.iterate(origin.add(-radius, -yRadius, -radius), origin.add(radius, yRadius, radius))) {
         BlockEntity lines = level.getBlockEntity(cursor);
         if (lines instanceof SignBlockEntity) {
            SignBlockEntity sign = (SignBlockEntity)lines;
            List<String> linesx = new ArrayList<>(4);

            for (int line = 0; line < 4; line++) {
               linesx.add(sign.getFrontText().getMessage(line, false).getString());
               linesx.add(sign.getBackText().getMessage(line, false).getString());
            }

            if (matchesStorageLabel(linesx, kind)) {
               BlockPos chest = findChestNear(level, cursor, 2);
               if (chest != null) {
                  return chest;
               }
            }
         }
      }

      return null;
   }

   private BlockPos findNearestChest(ClientWorld level, ClientPlayerEntity player, BlockPos origin) {
      int radius = Math.min(16, intValue(this.farmRadius) + 4);
      int yRadius = Math.min(8, intValue(this.verticalScan));
      BlockPos best = null;
      double bestDistance = Double.POSITIVE_INFINITY;

      for (BlockPos cursor : BlockPos.iterate(origin.add(-radius, -yRadius, -radius), origin.add(radius, yRadius, radius))) {
         if (isNormalChest(level.getBlockState(cursor))) {
            double distance = player.getEntityPos().squaredDistanceTo(Vec3d.ofCenter(cursor));
            if (distance < bestDistance) {
               bestDistance = distance;
               best = cursor.toImmutable();
            }
         }
      }

      return best;
   }

   private static BlockPos findChestNear(ClientWorld level, BlockPos sign, int radius) {
      BlockPos best = null;
      double bestDistance = Double.POSITIVE_INFINITY;

      for (BlockPos cursor : BlockPos.iterate(sign.add(-radius, -radius, -radius), sign.add(radius, radius, radius))) {
         if (isNormalChest(level.getBlockState(cursor))) {
            double distance = cursor.getSquaredDistance(sign);
            if (distance < bestDistance) {
               bestDistance = distance;
               best = cursor.toImmutable();
            }
         }
      }

      return best;
   }

   static boolean matchesStorageLabel(Iterable<String> lines, AppleFarmerFeature.StorageKind kind) {
      for (String line : lines) {
         String normalized = normalizeLabel(line);
         if (kind != AppleFarmerFeature.StorageKind.APPLES || !normalized.contains("apple") && !normalized.contains("яблок")) {
            if (kind != AppleFarmerFeature.StorageKind.BONES || !normalized.contains("bone") && !normalized.contains("кост")) {
               continue;
            }

            return true;
         }

         return true;
      }

      return false;
   }

   static String normalizeLabel(String value) {
      return value == null ? "" : value.replaceAll("§.", "").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
   }

   private BlockPos findPlantingTarget(ClientWorld level) {
      int radius = intValue(this.farmRadius);
      int yRadius = Math.min(3, intValue(this.verticalScan));

      for (BlockPos base : BlockPos.iterate(this.farmCenter.add(-radius, -yRadius, -radius), this.farmCenter.add(radius, yRadius, radius))) {
         BlockState state = level.getBlockState(base);
         if ((state.isOf(Blocks.DIRT) || state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.COARSE_DIRT) || state.isOf(Blocks.PODZOL))
            && level.isAir(base.up())
            && !treeNearby(level, base.up(), 2)) {
            return base.toImmutable();
         }
      }

      return null;
   }

   private static boolean treeNearby(ClientWorld level, BlockPos center, int radius) {
      for (BlockPos cursor : BlockPos.iterate(center.add(-radius, -1, -radius), center.add(radius, 3, radius))) {
         BlockState state = level.getBlockState(cursor);
         if (state.isOf(Blocks.OAK_SAPLING) || state.isOf(Blocks.OAK_LOG) || state.isOf(Blocks.OAK_WOOD)) {
            return true;
         }
      }

      return false;
   }

   private BlockPos findNearestBlock(ClientWorld level, ClientPlayerEntity player, boolean saplingOnly, Block... blocks) {
      if (this.farmCenter == null) {
         return null;
      } else {
         int radius = intValue(this.farmRadius);
         int yRadius = intValue(this.verticalScan);
         BlockPos best = null;
         double bestDistance = Double.POSITIVE_INFINITY;

         for (BlockPos cursor : BlockPos.iterate(this.farmCenter.add(-radius, -yRadius, -radius), this.farmCenter.add(radius, yRadius, radius))) {
            BlockState state = level.getBlockState(cursor);
            if (matchesAny(state, blocks) && (!saplingOnly || state.isOf(Blocks.OAK_SAPLING))) {
               double distance = player.getEntityPos().squaredDistanceTo(Vec3d.ofCenter(cursor));
               if (distance < bestDistance) {
                  bestDistance = distance;
                  best = cursor.toImmutable();
               }
            }
         }

         return best;
      }
   }

   private int findJunkSlot(ClientPlayerEntity player) {
      int totalSaplings = countItem(player, Items.OAK_SAPLING);
      int reserve = intValue(this.saplingReserve);

      for (int slot = 9; slot < 45; slot++) {
         ItemStack stack = player.playerScreenHandler.getSlot(slot).getStack();
         if (!stack.isEmpty()) {
            if (stack.isOf(Items.OAK_SAPLING) && totalSaplings > reserve) {
               return slot;
            }

            if (stack.isOf(Items.STICK)
               || stack.isOf(Items.OAK_LEAVES)
               || stack.isOf(Items.WHEAT_SEEDS)
               || stack.isOf(Items.BEETROOT_SEEDS)
               || stack.isOf(Items.MELON_SEEDS)
               || stack.isOf(Items.PUMPKIN_SEEDS)) {
               return slot;
            }
         }
      }

      return -1;
   }

   private AppleFarmerFeature.FarmerSignals maintenanceSignals(
      ClientPlayerEntity player, boolean ignoreAppleStorage, boolean ignoreBoneStorage, boolean timedOut
   ) {
      int apples = countItem(player, Items.APPLE);
      boolean deposit = !ignoreAppleStorage
         && this.tick >= this.skipAppleStorageUntil
         && apples > 0
         && (apples >= intValue(this.depositAppleStacks) * 64 || freeSlots(player) <= 2);
      boolean fetchBones = !ignoreBoneStorage
         && this.takeBones.getValue()
         && this.tick >= this.skipBoneStorageUntil
         && countItem(player, Items.BONE_MEAL) < intValue(this.boneMealReserve);
      ItemStack repairTool = findRepairTool(player, this.repairBelow.getValue());
      return signals(
         true,
         false,
         false,
         this.dropJunk.getValue() && this.findJunkSlot(player) >= 0,
         deposit,
         false,
         false,
         false,
         fetchBones,
         countItem(player, Items.BONE) > 0,
         !repairTool.isEmpty(),
         countItem(player, Items.EXPERIENCE_BOTTLE) > 0,
         this.buyXpBottles.getValue() && !this.xpBottleBuyCommand.getValue().isBlank(),
         timedOut
      );
   }

   private static AppleFarmerFeature.FarmerSignals signals(
      boolean atFarm,
      boolean workRemaining,
      boolean hasResource,
      boolean hasJunk,
      boolean shouldDepositApples,
      boolean destinationKnown,
      boolean atDestination,
      boolean inventoryWorkRemaining,
      boolean shouldFetchBones,
      boolean hasBones,
      boolean needsRepair,
      boolean hasBottles,
      boolean canBuyBottles,
      boolean timedOut
   ) {
      return new AppleFarmerFeature.FarmerSignals(
         atFarm,
         workRemaining,
         hasResource,
         hasJunk,
         shouldDepositApples,
         destinationKnown,
         atDestination,
         inventoryWorkRemaining,
         shouldFetchBones,
         hasBones,
         needsRepair,
         hasBottles,
         canBuyBottles,
         timedOut
      );
   }

   static AppleFarmerFeature.Phase nextPhase(AppleFarmerFeature.Phase phase, AppleFarmerFeature.FarmerSignals signals) {
      return switch (phase) {
         case RETURN_HOME -> !signals.atFarm() && !signals.timedOut() ? AppleFarmerFeature.Phase.RETURN_HOME : AppleFarmerFeature.Phase.FIND_FARM;
         case FIND_FARM -> signals.atFarm() ? AppleFarmerFeature.Phase.PLANT : AppleFarmerFeature.Phase.APPROACH_FARM;
         case APPROACH_FARM -> signals.atFarm()
         ? AppleFarmerFeature.Phase.PLANT
         : (signals.timedOut() ? AppleFarmerFeature.Phase.RETURN_HOME : AppleFarmerFeature.Phase.APPROACH_FARM);
         case PLANT -> signals.workRemaining() && signals.hasResource() && !signals.timedOut() ? AppleFarmerFeature.Phase.PLANT : AppleFarmerFeature.Phase.GROW;
         case GROW -> signals.workRemaining() && signals.hasResource() && !signals.timedOut()
         ? AppleFarmerFeature.Phase.GROW
         : AppleFarmerFeature.Phase.BREAK_LEAVES;
         case BREAK_LEAVES -> signals.workRemaining() && !signals.timedOut() ? AppleFarmerFeature.Phase.BREAK_LEAVES : AppleFarmerFeature.Phase.BREAK_LOGS;
         case BREAK_LOGS -> signals.workRemaining() && !signals.timedOut() ? AppleFarmerFeature.Phase.BREAK_LOGS : AppleFarmerFeature.Phase.DROP_JUNK;
         case DROP_JUNK -> signals.hasJunk() && !signals.timedOut() ? AppleFarmerFeature.Phase.DROP_JUNK : maintenancePhase(signals);
         case DEPOSIT_APPLES_FIND -> signals.destinationKnown()
         ? AppleFarmerFeature.Phase.DEPOSIT_APPLES_MOVE
         : (signals.timedOut() ? maintenanceAfterApples(signals) : AppleFarmerFeature.Phase.DEPOSIT_APPLES_FIND);
         case DEPOSIT_APPLES_MOVE -> signals.atDestination()
         ? AppleFarmerFeature.Phase.DEPOSIT_APPLES_OPEN
         : (signals.timedOut() ? maintenanceAfterApples(signals) : AppleFarmerFeature.Phase.DEPOSIT_APPLES_MOVE);
         case DEPOSIT_APPLES_OPEN -> signals.inventoryWorkRemaining() && !signals.timedOut()
         ? AppleFarmerFeature.Phase.DEPOSIT_APPLES_OPEN
         : maintenanceAfterApples(signals);
         case BONES_FIND -> signals.destinationKnown()
         ? AppleFarmerFeature.Phase.BONES_MOVE
         : (signals.timedOut() ? maintenanceAfterBones(signals) : AppleFarmerFeature.Phase.BONES_FIND);
         case BONES_MOVE -> signals.atDestination()
         ? AppleFarmerFeature.Phase.BONES_OPEN
         : (signals.timedOut() ? maintenanceAfterBones(signals) : AppleFarmerFeature.Phase.BONES_MOVE);
         case BONES_OPEN -> signals.inventoryWorkRemaining() && !signals.timedOut()
         ? AppleFarmerFeature.Phase.BONES_OPEN
         : AppleFarmerFeature.Phase.CRAFT_BONE_MEAL;
         case CRAFT_BONE_MEAL -> signals.hasBones() && signals.workRemaining() && !signals.timedOut()
         ? AppleFarmerFeature.Phase.CRAFT_BONE_MEAL
         : repairPhase(signals);
         case BUY_BOTTLES -> signals.hasBottles()
         ? AppleFarmerFeature.Phase.REPAIR
         : (!signals.timedOut() && signals.canBuyBottles() ? AppleFarmerFeature.Phase.BUY_BOTTLES : AppleFarmerFeature.Phase.PLANT);
         case REPAIR -> signals.needsRepair() && signals.hasBottles() && !signals.timedOut() ? AppleFarmerFeature.Phase.REPAIR : AppleFarmerFeature.Phase.PLANT;
      };
   }

   private static AppleFarmerFeature.Phase maintenancePhase(AppleFarmerFeature.FarmerSignals signals) {
      if (signals.shouldDepositApples()) {
         return AppleFarmerFeature.Phase.DEPOSIT_APPLES_FIND;
      } else if (signals.hasBones()) {
         return AppleFarmerFeature.Phase.CRAFT_BONE_MEAL;
      } else {
         return signals.shouldFetchBones() ? AppleFarmerFeature.Phase.BONES_FIND : repairPhase(signals);
      }
   }

   private static AppleFarmerFeature.Phase maintenanceAfterApples(AppleFarmerFeature.FarmerSignals signals) {
      if (signals.hasBones()) {
         return AppleFarmerFeature.Phase.CRAFT_BONE_MEAL;
      } else {
         return signals.shouldFetchBones() ? AppleFarmerFeature.Phase.BONES_FIND : repairPhase(signals);
      }
   }

   private static AppleFarmerFeature.Phase maintenanceAfterBones(AppleFarmerFeature.FarmerSignals signals) {
      return signals.hasBones() ? AppleFarmerFeature.Phase.CRAFT_BONE_MEAL : repairPhase(signals);
   }

   private static AppleFarmerFeature.Phase repairPhase(AppleFarmerFeature.FarmerSignals signals) {
      if (!signals.needsRepair()) {
         return AppleFarmerFeature.Phase.PLANT;
      } else if (signals.hasBottles()) {
         return AppleFarmerFeature.Phase.REPAIR;
      } else {
         return signals.canBuyBottles() ? AppleFarmerFeature.Phase.BUY_BOTTLES : AppleFarmerFeature.Phase.PLANT;
      }
   }

   private void transition(AppleFarmerFeature.Phase next) {
      AppleFarmerFeature.Phase previous = this.state.state();
      if (this.state.transition(next, this.tick)) {
         this.cancelNavigation();
         this.activeWorkPos = null;
         this.miningIssued = false;
         this.lastNavigationTick = -4611686018427387904L;
         this.homeCommandSent = false;
         this.buyCommandSent = false;
         if (previous == AppleFarmerFeature.Phase.REPAIR && next != AppleFarmerFeature.Phase.REPAIR) {
            this.restoreRepairTool(mc.player);
            this.restoreRotation(mc.player);
         }

         if (next == AppleFarmerFeature.Phase.PLANT && previous != AppleFarmerFeature.Phase.FIND_FARM && previous != AppleFarmerFeature.Phase.APPROACH_FARM) {
            this.completedCycles++;
            this.plantedThisCycle = 0;
            this.boneMealUsedThisCycle = 0;
            this.blocksBrokenThisCycle = 0;
         }

         if (next != AppleFarmerFeature.Phase.CRAFT_BONE_MEAL) {
            this.craftStep = 0;
            this.craftSourceSlot = -1;
         }
      }
   }

   private boolean beginNavigation(NavigationOptions options) {
      if (!this.navigator.isAvailable()) {
         return false;
      } else {
         try {
            this.navigator.begin(PveManagerFeature.INSTANCE.configureNavigation(options));
            this.navigationActive = true;
            return true;
         } catch (LinkageError | RuntimeException var3) {
            this.navigationActive = false;
            return false;
         }
      }
   }

   private void navigateTo(BlockPos target, int radius, NavigationOptions options) {
      if (target != null && this.beginNavigation(options)) {
         Optional<BlockPos> currentGoal = this.navigator.currentGoal();
         boolean sameGoal = currentGoal.isPresent() && currentGoal.get().equals(target);
         if (!sameGoal || !this.navigator.isPathing() && this.tick - this.lastNavigationTick >= 40L) {
            try {
               this.navigator.pathTo(target, radius);
               this.lastNavigationTick = this.tick;
            } catch (LinkageError | RuntimeException var7) {
               this.lastNavigationTick = this.tick;
            }
         }
      }
   }

   private void cancelNavigation() {
      if (this.navigationActive) {
         try {
            this.navigator.cancel();
         } catch (LinkageError | RuntimeException var2) {
            this.navigationActive = false;
         }
      }
   }

   private void endNavigation() {
      if (this.navigationActive) {
         try {
            this.navigator.end();
         } catch (LinkageError | RuntimeException var5) {
            this.navigator.cancel();
         } finally {
            this.navigationActive = false;
         }
      }
   }

   private boolean actionReady() {
      return this.tick - this.lastActionTick >= 4L;
   }

   private boolean selectHotbarItem(ClientPlayerEntity player, Item item) {
      int slot = findPlayerSlot(player, item);
      if (slot < 0) {
         return false;
      } else {
         int selected = player.getInventory().getSelectedSlot();
         if (slot >= 36 && slot < 45) {
            player.getInventory().setSelectedSlot(slot - 36);
            return true;
         } else {
            this.clickPlayerMenu(player, slot, selected, SlotActionType.SWAP);
            return player.getMainHandStack().isOf(item);
         }
      }
   }

   private void useFromPlayerSlot(ClientPlayerEntity player, int slot) {
      int selected = player.getInventory().getSelectedSlot();
      if (slot >= 36 && slot < 45) {
         int hotbar = slot - 36;
         player.getInventory().setSelectedSlot(hotbar);
         mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
         player.swingHand(Hand.MAIN_HAND);
         player.getInventory().setSelectedSlot(selected);
      } else {
         this.clickPlayerMenu(player, slot, selected, SlotActionType.SWAP);
         mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
         player.swingHand(Hand.MAIN_HAND);
         this.clickPlayerMenu(player, slot, selected, SlotActionType.SWAP);
      }
   }

   private void swapWithOffhand(ClientPlayerEntity player, int slot) {
      this.clickPlayerMenu(player, slot, 40, SlotActionType.SWAP);
   }

   private void restoreRepairTool(ClientPlayerEntity player) {
      if (this.repairToolSourceSlot >= 0) {
         if (player != null && player.currentScreenHandler == player.playerScreenHandler && player.playerScreenHandler.isValid(this.repairToolSourceSlot)) {
            this.swapWithOffhand(player, this.repairToolSourceSlot);
         }

         this.repairToolSourceSlot = -1;
      }
   }

   private void restoreCraftingInventory(ClientPlayerEntity player) {
      if (player != null && mc.interactionManager != null && player.currentScreenHandler == player.playerScreenHandler) {
         if (!player.playerScreenHandler.getCursorStack().isEmpty()) {
            int destination = validEmptySlot(player, this.craftSourceSlot) ? this.craftSourceSlot : findEmptyPlayerSlot(player);
            if (destination >= 0) {
               this.clickPlayerMenu(player, destination, 0, SlotActionType.PICKUP);
            }
         }

         ItemStack input = player.playerScreenHandler.getSlot(1).getStack();
         if (!input.isEmpty() && player.playerScreenHandler.getCursorStack().isEmpty()) {
            int destination = validEmptySlot(player, this.craftSourceSlot) ? this.craftSourceSlot : findEmptyPlayerSlot(player);
            if (destination >= 0) {
               this.clickPlayerMenu(player, 1, 0, SlotActionType.PICKUP);
               this.clickPlayerMenu(player, destination, 0, SlotActionType.PICKUP);
            }
         }
      }
   }

   private static boolean validEmptySlot(ClientPlayerEntity player, int slot) {
      return slot >= 0 && player.playerScreenHandler.isValid(slot) && player.playerScreenHandler.getSlot(slot).getStack().isEmpty();
   }

   private static int findEmptyPlayerSlot(ClientPlayerEntity player) {
      for (int slot = 9; slot < 45; slot++) {
         if (player.playerScreenHandler.getSlot(slot).getStack().isEmpty()) {
            return slot;
         }
      }

      return -1;
   }

   private void clickPlayerMenu(ClientPlayerEntity player, int slot, int button, SlotActionType input) {
      if (mc.interactionManager != null && player.playerScreenHandler.isValid(slot)) {
         mc.interactionManager.clickSlot(player.playerScreenHandler.syncId, slot, button, input, player);
      }
   }

   private static int findPlayerContainerSlot(ScreenHandler menu, Predicate<ItemStack> predicate) {
      int start = ContainerLootService.containerSlotCount(menu);

      for (int slot = start; slot < menu.slots.size(); slot++) {
         ItemStack stack = menu.getSlot(slot).getStack();
         if (!stack.isEmpty() && predicate.test(stack)) {
            return slot;
         }
      }

      return -1;
   }

   private static int findPlayerSlot(ClientPlayerEntity player, Item item) {
      for (int slot = 36; slot < 45; slot++) {
         if (player.playerScreenHandler.getSlot(slot).getStack().isOf(item)) {
            return slot;
         }
      }

      for (int slotx = 9; slotx < 36; slotx++) {
         if (player.playerScreenHandler.getSlot(slotx).getStack().isOf(item)) {
            return slotx;
         }
      }

      return -1;
   }

   private static int countItem(ClientPlayerEntity player, Item item) {
      int count = 0;

      for (int slot = 9; slot < 45; slot++) {
         ItemStack stack = player.playerScreenHandler.getSlot(slot).getStack();
         if (stack.isOf(item)) {
            count += stack.getCount();
         }
      }

      if (player.getOffHandStack().isOf(item)) {
         count += player.getOffHandStack().getCount();
      }

      return count;
   }

   private static int freeSlots(ClientPlayerEntity player) {
      int free = 0;

      for (int slot = 9; slot < 45; slot++) {
         if (player.playerScreenHandler.getSlot(slot).getStack().isEmpty()) {
            free++;
         }
      }

      return free;
   }

   private static ItemStack findRepairTool(ClientPlayerEntity player, double thresholdPercent) {
      int slot = findRepairToolSlot(player, thresholdPercent);
      return slot < 0 ? ItemStack.EMPTY : player.playerScreenHandler.getSlot(slot).getStack();
   }

   private static int findRepairToolSlot(ClientPlayerEntity player, double thresholdPercent) {
      int bestSlot = -1;
      double worstRemaining = Double.POSITIVE_INFINITY;

      for (int slot = 9; slot < 45; slot++) {
         ItemStack stack = player.playerScreenHandler.getSlot(slot).getStack();
         if (needsRepair(stack, thresholdPercent) && enchantmentLevel(stack, "mending") > 0) {
            double remaining = remainingDurability(stack);
            if (remaining < worstRemaining) {
               worstRemaining = remaining;
               bestSlot = slot;
            }
         }
      }

      return bestSlot;
   }

   private static boolean needsRepair(ItemStack stack, double thresholdPercent) {
      return !stack.isEmpty() && stack.isDamageable() && enchantmentLevel(stack, "mending") > 0 && remainingDurability(stack) < thresholdPercent;
   }

   private static double remainingDurability(ItemStack stack) {
      return !stack.isDamageable() ? 100.0 : (double)(stack.getMaxDamage() - stack.getDamage()) * 100.0 / (double)Math.max(1, stack.getMaxDamage());
   }

   private static int enchantmentLevel(ItemStack stack, String path) {
      if (stack.isEmpty()) {
         return 0;
      } else {
         for (Entry<RegistryEntry<Enchantment>> entry : stack.getEnchantments().getEnchantmentEntries()) {
            Optional<RegistryKey<Enchantment>> key = ((RegistryEntry)entry.getKey()).getKey();
            if (key.isPresent() && key.get().getValue().getPath().equals(path)) {
               return entry.getIntValue();
            }
         }

         return 0;
      }
   }

   private void lookAt(ClientPlayerEntity player, Vec3d target) {
      if (PveManagerFeature.INSTANCE.rotate.getValue()) {
         this.saveRotation(player);
         Vec3d delta = target.subtract(player.getEyePos());
         double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
         float yaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
         float pitch = (float)(-Math.toDegrees(Math.atan2(delta.y, horizontal)));
         player.setYaw(yaw);
         player.setPitch(pitch);
      }
   }

   private void saveRotation(ClientPlayerEntity player) {
      if (!this.rotationSaved && player != null) {
         this.savedYaw = player.getYaw();
         this.savedPitch = player.getPitch();
         this.rotationSaved = true;
      }
   }

   private void restoreRotation(ClientPlayerEntity player) {
      if (this.rotationSaved && player != null) {
         player.setYaw(this.savedYaw);
         player.setPitch(this.savedPitch);
         this.rotationSaved = false;
      }
   }

   private static boolean at(ClientPlayerEntity player, BlockPos position, double radius) {
      return position != null && player.getEntityPos().squaredDistanceTo(Vec3d.ofCenter(position)) <= radius * radius;
   }

   private static boolean matchesAny(BlockState state, Block... blocks) {
      for (Block block : blocks) {
         if (state.isOf(block)) {
            return true;
         }
      }

      return false;
   }

   private static boolean isNormalChest(BlockState state) {
      return state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST);
   }

   private static int intValue(NumberSetting setting) {
      return (int)Math.round(setting.getValue());
   }

   @Environment(EnvType.CLIENT)
   static record FarmerSignals(
      boolean atFarm,
      boolean workRemaining,
      boolean hasResource,
      boolean hasJunk,
      boolean shouldDepositApples,
      boolean destinationKnown,
      boolean atDestination,
      boolean inventoryWorkRemaining,
      boolean shouldFetchBones,
      boolean hasBones,
      boolean needsRepair,
      boolean hasBottles,
      boolean canBuyBottles,
      boolean timedOut
   ) {
   }

   @Environment(EnvType.CLIENT)
   public static enum Phase {
      RETURN_HOME,
      FIND_FARM,
      APPROACH_FARM,
      PLANT,
      GROW,
      BREAK_LEAVES,
      BREAK_LOGS,
      DROP_JUNK,
      DEPOSIT_APPLES_FIND,
      DEPOSIT_APPLES_MOVE,
      DEPOSIT_APPLES_OPEN,
      BONES_FIND,
      BONES_MOVE,
      BONES_OPEN,
      CRAFT_BONE_MEAL,
      BUY_BOTTLES,
      REPAIR;
   }

   @Environment(EnvType.CLIENT)
   static enum StorageKind {
      APPLES,
      BONES;
   }
}
