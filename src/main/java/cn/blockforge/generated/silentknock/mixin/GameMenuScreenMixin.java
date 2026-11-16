package cn.blockforge.generated.silentknock.mixin;

import cn.blockforge.generated.silentknock.gui.SpaQuickKnockScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在暂停菜单（ESC）左下角放一个「SPA 敲门」按钮。
 *
 * <p>只有当前服务器绑定了 SPA 密钥文件时才出现；点开就是 {@link SpaQuickKnockScreen}，
 * 可以在游戏里随时临时敲一个额外端口（语音模组之类），不用改配置文件。
 */
@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void silentspa$addKnockButton(CallbackInfo ci) {
        GameMenuScreen menu = (GameMenuScreen) (Object) this;
        if (!menu.shouldShowMenu() || !SpaQuickKnockScreen.isAvailable()) {
            return;
        }
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("silent_knock.knock.button"),
                        button -> {
                            if (this.client != null) {
                                this.client.setScreen(new SpaQuickKnockScreen((Screen) (Object) this));
                            }
                        })
                .dimensions(4, this.height - 24, 78, 20)
                .tooltip(Tooltip.of(Text.translatable("silent_knock.knock.button.tooltip")))
                .build());
    }
}
