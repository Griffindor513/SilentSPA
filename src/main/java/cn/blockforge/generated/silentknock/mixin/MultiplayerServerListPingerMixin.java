package cn.blockforge.generated.silentknock.mixin;

import cn.blockforge.generated.silentknock.logic.SpaManager;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 刷新服务器列表（MOTD Ping）之前，对绑定了 SPA 的服务器先把端口敲开。
 *
 * <p>线程很重要：1.21.8 的原版把这个方法交给「Server Pinger」后台线程池
 * （{@code MultiplayerServerListWidget.SERVER_PINGER_THREAD_POOL}）去跑，
 * 所以我们在这里等敲门结果是安全的 —— 卡住的只是一条后台线程，画面照常刷。
 *
 * <p>反过来，<b>绝对不能</b>把原版的 {@code add} 拖回渲染主线程：它内部要做 DNS / SRV
 * 解析（{@code AllowedAddressResolver}），在渲染线程上一解析就是几秒钟起步，
 * 游戏看起来就是“卡死”。
 */
@Mixin(MultiplayerServerListPinger.class)
public abstract class MultiplayerServerListPingerMixin {
    @Inject(method = "add", at = @At("HEAD"))
    private void silentspa$knockBeforePing(ServerInfo info, Runnable onDone, Runnable onError, CallbackInfo ci) {
        SpaManager.knockBeforePing(info);
    }
}
