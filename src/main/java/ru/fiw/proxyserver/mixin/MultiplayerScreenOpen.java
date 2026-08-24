package ru.fiw.proxyserver.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.fiw.proxyserver.Config;
import ru.fiw.proxyserver.GuiProxy;
import ru.fiw.proxyserver.ProxyServer;

@Environment(EnvType.CLIENT)
@Mixin({MultiplayerScreen.class})
public abstract class MultiplayerScreenOpen extends Screen {
   private static final int BUTTON_WIDTH = 120;
   private static final int BUTTON_HEIGHT = 20;
   private static final int RIGHT_MARGIN = 2;
   private static final int VIAFABRICPLUS_WIDTH = 106;

   protected MultiplayerScreenOpen(Text title) {
      super(title);
   }

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void proxyserver$multiplayerGuiOpen(CallbackInfo callbackInfo) {
      Config.activateForPlayer(this.client.getSession().getUsername());
      this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Proxy: " + ProxyServer.getLastUsedProxyIp()), button -> this.client.setScreen(new GuiProxy(this)))
            .dimensions(this.width - 120 - 106 - 2, 5, 120, 20)
            .build()
      );
   }
}
