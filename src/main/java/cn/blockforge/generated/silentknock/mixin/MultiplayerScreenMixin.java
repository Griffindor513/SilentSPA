package cn.blockforge.generated.silentknock.mixin;

import cn.blockforge.generated.silentknock.logic.ClockCheck;
import cn.blockforge.generated.silentknock.logic.SpaManager;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 进入多人游戏列表时做两件事：
 *
 * <ol>
 *   <li><b>自动敲门</b>：列表里凡是绑定了 SPA 密钥的服务器，一进来就先把端口敲开，
 *       不用玩家先点一下那条服务器。敲完再把这一行重新 Ping 一次，
 *       这样 MOTD / 延迟就能直接显示出来（原版第一次 Ping 可能还在门外的）。</li>
 *   <li><b>校验本机时间</b>：顺手和网络时间对一下，偏差太大就提醒玩家。</li>
 * </ol>
 *
 * <p>两件事都是异步的：这里只负责“发出去”，绝不在渲染线程上等网络，
 * 所以打开列表不会卡。
 */
@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixin {
    // require = 0：注入失败最多是不会自动敲门，不会把客户端搞崩
    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void silentspa$onOpened(CallbackInfo ci) {
        MultiplayerScreen screen = (MultiplayerScreen) (Object) this;
        ClockCheck.refreshAsync(true);
        SpaManager.onServerListOpened(screen.getServerList());
    }
}
