package cn.blockforge.generated.silentknock.mixin;

import cn.blockforge.generated.silentknock.logic.SpaManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在真正发起 TCP 连接之前插一脚。
 *
 * <p>注入点里只做判断并立刻返回，绝不阻塞：密钥读取、加密、UDP 发送和延迟
 * 全部交给 {@link SpaManager} 的 CompletableFuture 链，最后再回主线程调用原版 connect。
 */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {
    @Inject(
            method = "connect(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/network/ServerAddress;Lnet/minecraft/client/network/ServerInfo;ZLnet/minecraft/client/network/CookieStorage;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void silentspa$beforeConnect(Screen parent, MinecraftClient client, ServerAddress address,
                                                ServerInfo info, boolean quickPlay, CookieStorage cookieStorage,
                                                CallbackInfo ci) {
        if (SpaManager.tryHandleConnect(info, parent, client, address, quickPlay, cookieStorage)) {
            ci.cancel();
        }
    }
}
