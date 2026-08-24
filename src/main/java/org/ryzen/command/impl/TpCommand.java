package org.ryzen.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround;
import net.minecraft.util.math.Vec3d;
import org.ryzen.command.ClientCommand;
import org.ryzen.utils.text.ChatUtil;

@Environment(EnvType.CLIENT)
public final class TpCommand extends ClientCommand {
   private static final int SETTLE_PACKETS = 3;

   public TpCommand() {
      super("tp", "Teleports to a player by name", ":round_pushpin:");
   }

   @Override
   public List<String> aliases() {
      return List.of("teleport");
   }

   @Override
   public void build(LiteralArgumentBuilder<Object> builder) {
      builder.executes(context -> this.showUsage());
      builder.then(
         RequiredArgumentBuilder.argument("name", StringArgumentType.word())
            .suggests((context, suggestions) -> suggestPlayers(suggestions))
            .executes(this::teleport)
      );
   }

   private int showUsage() {
      ChatUtil.usage("tp <name>  •  teleports to a player in your world");
      return 1;
   }

   private int teleport(CommandContext<Object> context) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.world != null) {
         String name = StringArgumentType.getString(context, "name");
         PlayerEntity target = findPlayer(mc, name);
         if (target == null) {
            ChatUtil.error("Player not found  •  " + name);
            return 0;
         } else if (target == player) {
            ChatUtil.error("That is you");
            return 0;
         } else {
            Vec3d position = target.getEntityPos();

            for (int index = 0; index < 3; index++) {
               player.networkHandler.sendPacket(new OnGroundOnly(player.isOnGround(), player.horizontalCollision));
            }

            player.networkHandler.sendPacket(new PositionAndOnGround(position.x, position.y, position.z, false, player.horizontalCollision));
            player.setPosition(position.x, position.y, position.z);
            ChatUtil.success(
               String.format(Locale.ROOT, "Teleported to %s  •  %.1f %.1f %.1f", target.getGameProfile().name(), position.x, position.y, position.z)
            );
            return 1;
         }
      } else {
         ChatUtil.error("Not in a world");
         return 0;
      }
   }

   private static PlayerEntity findPlayer(MinecraftClient mc, String name) {
      for (PlayerEntity player : mc.world.getPlayers()) {
         if (player != null && player.getGameProfile().name().equalsIgnoreCase(name)) {
            return player;
         }
      }

      return null;
   }

   private static CompletableFuture<Suggestions> suggestPlayers(SuggestionsBuilder builder) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world == null) {
         return builder.buildFuture();
      } else {
         String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
         List<String> names = new ArrayList<>();

         for (PlayerEntity player : mc.world.getPlayers()) {
            if (player != null && player != mc.player) {
               names.add(player.getGameProfile().name());
            }
         }

         names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(remaining)).forEach(builder::suggest);
         return builder.buildFuture();
      }
   }
}
