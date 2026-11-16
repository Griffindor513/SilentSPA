package cn.blockforge.generated.silentknock.mixin;

import cn.blockforge.generated.silentknock.gui.SpaSettingsScreen;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在原版「选项」界面里塞一个 SilentSPA 按钮，点开就是 {@link SpaSettingsScreen}。
 *
 * <p>为什么放在这里：不装 ModMenu 这类「模组列表」模组时，玩家没有别的地方能打开
 * 本模组的设置。原版底栏只有一个居中的「完成」按钮，左边是空的，所以把按钮挤到它右侧、
 * 两个并排居中；窗口大小一变（{@code refreshWidgetPositions}）就重新摆一次。
 */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {
    private static final int SILENTSPA_GAP = 8;
    private static final int SILENTSPA_BUTTON_WIDTH = 100;

    private ButtonWidget silentspa$settingsButton;

    protected OptionsScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void silentspa$addSettingsButton(CallbackInfo ci) {
        this.silentspa$settingsButton = this.addDrawableChild(ButtonWidget
                .builder(Text.translatable("silent_knock.settings.button"),
                        button -> {
                            if (this.client != null) {
                                this.client.setScreen(new SpaSettingsScreen((Screen) (Object) this));
                            }
                        })
                .dimensions(0, 0, SILENTSPA_BUTTON_WIDTH, 20)
                .tooltip(Tooltip.of(Text.translatable("silent_knock.settings.button.tooltip")))
                .build());
        this.silentspa$placeButton();
    }

    @Inject(method = "refreshWidgetPositions", at = @At("TAIL"))
    private void silentspa$placeButtonOnResize(CallbackInfo ci) {
        this.silentspa$placeButton();
    }

    /** 把按钮放到原版「完成」按钮右边；找不到「完成」就退回左下角。 */
    private void silentspa$placeButton() {
        ButtonWidget mine = this.silentspa$settingsButton;
        if (mine == null) {
            return;
        }
        ButtonWidget done = null;
        for (Element element : this.children()) {
            if (element instanceof ButtonWidget widget && widget != mine
                    && ScreenTexts.DONE.getString().equals(widget.getMessage().getString())) {
                done = widget;
                break;
            }
        }
        if (done == null) {
            mine.setPosition(6, this.height - 27);
            return;
        }
        int total = done.getWidth() + SILENTSPA_GAP + mine.getWidth();
        int x = Math.max(4, (this.width - total) / 2);
        done.setX(x);
        mine.setX(x + done.getWidth() + SILENTSPA_GAP);
        mine.setY(done.getY());
    }
}
