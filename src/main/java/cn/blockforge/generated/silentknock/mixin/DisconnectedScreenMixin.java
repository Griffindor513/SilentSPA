package cn.blockforge.generated.silentknock.mixin;

import cn.blockforge.generated.silentknock.logic.SpaManager;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「连接失败」界面的文案改写。
 *
 * <p>所有构造器最后都会落到带 {@link DisconnectionInfo} 的那个四参数构造器，
 * 所以这里只挂一个点就能覆盖所有“连接失败”界面。
 *
 * <p>只有本次连接是模组插手敲过门、而且还没走到登录阶段的那一次才会改写；
 * 普通被踢下线、白名单 / 版本不符这类失败保持原版文案。
 */
@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin {
    @Shadow
    @Final
    @Mutable
    private DisconnectionInfo info;

    @Inject(
            method = "<init>(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/text/Text;Lnet/minecraft/network/DisconnectionInfo;Lnet/minecraft/text/Text;)V",
            at = @At("RETURN")
    )
    private void silentspa$decorateReason(CallbackInfo ci) {
        DisconnectionInfo original = this.info;
        if (original == null) {
            return;
        }
        Text decorated;
        try {
            decorated = SpaManager.decorateDisconnect(original.reason());
        } catch (Throwable t) {
            // 任何意外都退回原版文案，绝不让界面开不出来
            return;
        }
        if (decorated != null && decorated != original.reason()) {
            this.info = new DisconnectionInfo(decorated, original.report(), original.bugReportLink());
        }
    }
}
