package org.ryzen.pve.server;

import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;

@Environment(EnvType.CLIENT)
public interface ServerAdapter {
   ServerProfile profile();

   default void sendCommand(ClientPlayerEntity player, String command) {
      if (player != null && command != null && !command.isBlank()) {
         String normalized = command.charAt(0) == '/' ? command.substring(1) : command;
         player.networkHandler.sendChatCommand(normalized);
      }
   }

   default Optional<String> anarchyCommand(int number) {
      return Optional.empty();
   }

   default Optional<String> homeCommand(String home) {
      return Optional.empty();
   }

   default Optional<String> hubCommand() {
      return Optional.empty();
   }

   default Optional<String> auctionCommand() {
      return Optional.empty();
   }

   default Optional<String> reportCommand(String playerName) {
      return Optional.empty();
   }
}
