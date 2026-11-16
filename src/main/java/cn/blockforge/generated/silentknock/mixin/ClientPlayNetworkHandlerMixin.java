package cn.blockforge.generated.silentknock.mixin;

import cn.blockforge.generated.silentknock.logic.SpaManager;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 真正进了世界（拿到游戏内网络处理器）就说明这次敲门 + 连接成功了。
 *
 * <p>清掉“本次连接尝试”的记录，这样之后玩到一半被服务器踢下线时，
 * 不会有模组再去改写那条断开提示。
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void silentspa$onJoinedServer(CallbackInfo ci) {
        SpaManager.onJoined();
    }
}
