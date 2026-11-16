package cn.blockforge.generated.silentknock.mixin;

import cn.blockforge.generated.silentknock.SilentSpaClient;
import cn.blockforge.generated.silentknock.SpaHooks;
import cn.blockforge.generated.silentknock.gui.SpaFilePickerScreen;
import cn.blockforge.generated.silentknock.gui.SpaKeyRow;
import cn.blockforge.generated.silentknock.logic.SpaFeedback;
import cn.blockforge.generated.silentknock.logic.SpaManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.AddServerScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

/**
 * 在“添加 / 编辑服务器”界面加一行：SPA 密钥文件 + 可选端口。
 *
 * <p>已经绑定了密钥时，这一行中间会多出一个“解绑”小按钮：点一下就把这台服务器
 * 和密钥文件的绑定去掉（只是不再用它敲门，文件本身还留在电脑里，别的服务器还能用）。
 *
 * <p>为了让打开文件选择器再返回时不丢用户已经输入的服务器名 / 地址，
 * 会在离开前暂存两个输入框的文本，回到本界面时再填回去。
 */
@Mixin(AddServerScreen.class)
public abstract class AddServerScreenMixin extends Screen {
    /** 这一行控件的总宽度，原版两个输入框也是这么宽。 */
    private static final int ROW_WIDTH = 200;

    @Shadow
    private ServerInfo server;

    @Shadow
    private TextFieldWidget addressField;

    @Shadow
    private TextFieldWidget serverNameField;

    @Unique
    private ButtonWidget silentspa$keyButton;

    @Unique
    private ButtonWidget silentspa$unbindButton;

    @Unique
    private TextFieldWidget silentspa$portField;

    @Unique
    private String silentspa$draftName;

    @Unique
    private String silentspa$draftAddress;

    protected AddServerScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void silentspa$addWidgets(CallbackInfo ci) {
        if (this.server == null) {
            return;
        }
        // 打开文件选择器再回来时，把用户刚才输入的内容填回去
        if (this.silentspa$draftName != null) {
            this.serverNameField.setText(this.silentspa$draftName);
            this.addressField.setText(this.silentspa$draftAddress);
            this.silentspa$draftName = null;
            this.silentspa$draftAddress = null;
        }

        SpaHooks hooks = (SpaHooks) (Object) this.server;
        // 放在“资源包”下拉框和“完成”按钮之间的空隙里，不挤占原版控件
        int y = this.height / 4 + 93;
        int x = this.width / 2 - ROW_WIDTH / 2;

        // 宽度、文字和“解绑”按钮显不显示都由 applyLayout 统一算
        this.silentspa$keyButton = ButtonWidget.builder(Text.empty(), button -> this.silentspa$openPicker())
                .dimensions(x, y, 100, 20)
                .tooltip(SpaKeyRow.tooltip(null))
                .build();
        this.addDrawableChild(this.silentspa$keyButton);

        this.silentspa$unbindButton = ButtonWidget.builder(Text.translatable("silent_knock.button.unbind"),
                        button -> this.silentspa$unbind())
                .dimensions(x, y, SpaKeyRow.UNBIND_WIDTH, 20)
                .tooltip(Tooltip.of(Text.translatable("silent_knock.button.unbind.tooltip")))
                .build();
        this.addDrawableChild(this.silentspa$unbindButton);

        this.silentspa$portField = new TextFieldWidget(this.textRenderer,
                SpaKeyRow.portX(x, ROW_WIDTH), y, SpaKeyRow.PORT_WIDTH, 20,
                Text.translatable("silent_knock.port.hint"));
        this.silentspa$portField.setMaxLength(5);
        this.silentspa$portField.setPlaceholder(Text.translatable("silent_knock.port.hint"));
        this.silentspa$portField.setTooltip(Tooltip.of(Text.translatable("silent_knock.port.tooltip")));
        int port = hooks.silentspa$getSpaPort();
        if (port > 0) {
            this.silentspa$portField.setText(Integer.toString(port));
        }
        this.silentspa$portField.setChangedListener(text -> {
            int value = -1;
            try {
                if (!text.isBlank()) {
                    value = Integer.parseInt(text.strip());
                }
            } catch (NumberFormatException ignored) {
                value = -1;
            }
            hooks.silentspa$setSpaPort(value);
        });
        this.addDrawableChild(this.silentspa$portField);

        this.silentspa$applyLayout(hooks);
    }

    /** 按“有没有绑定”重新摆这一行控件：解绑按钮只在绑定了的时候出现。 */
    @Unique
    private void silentspa$applyLayout(SpaHooks hooks) {
        if (this.silentspa$keyButton == null || this.silentspa$unbindButton == null
                || this.silentspa$portField == null) {
            return;
        }
        boolean bound = SpaKeyRow.isBound(hooks.silentspa$getKeyPath());
        Text unbindLabel = Text.translatable("silent_knock.button.unbind");
        int unbindWidth = bound ? SpaKeyRow.unbindWidth(this.textRenderer, unbindLabel) : 0;
        int x = this.width / 2 - ROW_WIDTH / 2;
        int keyWidth = SpaKeyRow.keyWidth(ROW_WIDTH, unbindWidth);
        this.silentspa$keyButton.setWidth(keyWidth);
        this.silentspa$keyButton.setMessage(SpaKeyRow.label(this.textRenderer,
                hooks.silentspa$getKeyPath(), keyWidth));
        this.silentspa$keyButton.setTooltip(SpaKeyRow.tooltip(hooks.silentspa$getKeyPath()));
        this.silentspa$unbindButton.setMessage(unbindLabel);
        this.silentspa$unbindButton.setWidth(Math.max(SpaKeyRow.UNBIND_WIDTH, unbindWidth));
        this.silentspa$unbindButton.setX(SpaKeyRow.unbindX(x, ROW_WIDTH, unbindWidth));
        this.silentspa$unbindButton.visible = bound;
        this.silentspa$unbindButton.active = bound;
        this.silentspa$portField.setX(SpaKeyRow.portX(x, ROW_WIDTH));
    }

    /** 解绑：只清掉“这台服务器用哪个密钥”，删文件留给玩家自己去删。 */
    @Unique
    private void silentspa$unbind() {
        SpaHooks hooks = (SpaHooks) (Object) this.server;
        if (!SpaKeyRow.isBound(hooks.silentspa$getKeyPath())) {
            return;
        }
        String fileName = SpaKeyRow.fileName(hooks.silentspa$getKeyPath());
        hooks.silentspa$setKeyPath(null);
        hooks.silentspa$setSpaPort(-1);
        if (this.silentspa$portField != null) {
            this.silentspa$portField.setText("");
        }
        this.silentspa$applyLayout(hooks);
        String label = this.server.name == null || this.server.name.isBlank()
                ? this.addressField.getText()
                : this.server.name;
        SilentSpaClient.LOGGER.info("[SPA] 已解除服务器“{}”的密钥绑定（{}，文件保留）", label, fileName);
        SpaFeedback.keyUnbound(label);
    }

    @Unique
    private void silentspa$openPicker() {
        this.silentspa$draftName = this.serverNameField.getText();
        this.silentspa$draftAddress = this.addressField.getText();
        this.client.setScreen(new SpaFilePickerScreen((Screen) (Object) this, this.addressField.getText(), path -> {
            ((SpaHooks) (Object) this.server).silentspa$setKeyPath(path.toString());
            SilentSpaClient.LOGGER.info("[SPA] 已为服务器“{}”绑定密钥文件：{}", this.server.name, path);
            SpaManager.validateKeyFile(path);
        }));
    }
}
