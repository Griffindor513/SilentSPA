package cn.blockforge.generated.silentknock.mixin;

import cn.blockforge.generated.silentknock.logic.SpaManager;
import net.minecraft.client.network.ClientLoginNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 走到「登录阶段」说明 TCP 已经连上了：服务器要么本来就不需要 SPA，要么已经放行了本机。
 *
 * <p>于是后面再失败（白名单、版本不符、服务器已满……）就跟敲门没关系了，
 * 这里把「本次敲门尝试」的记录清掉，避免断开界面贴错提示。
 */
@Mixin(ClientLoginNetworkHandler.class)
public abstract class ClientLoginNetworkHandlerMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void silentspa$onLoginStarted(CallbackInfo ci) {
        SpaManager.onLoginStarted();
    }
}
