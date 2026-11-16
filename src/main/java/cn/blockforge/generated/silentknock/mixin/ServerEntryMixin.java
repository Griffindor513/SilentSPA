package cn.blockforge.generated.silentknock.mixin;

import cn.blockforge.generated.silentknock.gui.ListStatusLabel;
import cn.blockforge.generated.silentknock.gui.SpaBadge;
import cn.blockforge.generated.silentknock.logic.SpaManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给多人游戏列表里的服务器行加两样东西：
 *
 * <ol>
 *   <li><b>金色小盾牌</b>：绑定了 SPA 密钥的服务器，图标左上角压一个小金盾（10×10），
 *       玩家一眼就能分出哪几台是要敲门的 SPA 服务器。没绑定密钥的服务器什么都不画。</li>
 *   <li><b>名字右边的状态字</b>：正在敲门时写 “Knocking”，门敲完（或本来就不用敲门）写
 *       “Pinging”，见 {@link ListStatusLabel}。这两步以前会弹右上角的提示框，现在不弹了。</li>
 * </ol>
 */
@Mixin(MultiplayerServerListWidget.ServerEntry.class)
public abstract class ServerEntryMixin {
    @Shadow
    @Final
    private ServerInfo server;

    // require = 0：万一以后原版改了这个方法的写法，最多是没有盾牌，不至于让客户端崩掉
    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void silentspa$drawShield(DrawContext context, int index, int y, int x,
                                      int entryWidth, int entryHeight, int mouseX, int mouseY,
                                      boolean hovered, float tickProgress, CallbackInfo ci) {
        if (this.server == null) {
            return;
        }
        // 贴着这一行的左上角画，也就是服务器图标的左上角（图标就是从这里开始画的）
        if (SpaManager.hasKey(this.server)) {
            SpaBadge.draw(context, x, y);
        }
        // 名字右边的 “Knocking / Pinging”
        ListStatusLabel.draw(context, this.server, x, y, entryWidth);
    }
}
