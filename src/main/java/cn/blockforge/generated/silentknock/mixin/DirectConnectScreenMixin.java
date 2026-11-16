package cn.blockforge.generated.silentknock.mixin;

import cn.blockforge.generated.silentknock.SilentSpaClient;
import cn.blockforge.generated.silentknock.SpaHooks;
import cn.blockforge.generated.silentknock.config.DirectBindings;
import cn.blockforge.generated.silentknock.gui.SpaFilePickerScreen;
import cn.blockforge.generated.silentknock.gui.SpaKeyRow;
import cn.blockforge.generated.silentknock.logic.SpaFeedback;
import cn.blockforge.generated.silentknock.logic.SpaManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.DirectConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

/**
 * 让“直接连接”界面也能像“添加服务器”界面一样绑定 SPA 密钥。
 *
 * <p>原版这个界面只有一个地址输入框，没有服务器条目可编辑，所以模组在地址框下面
 * 补一行“SPA 密钥 + 端口”。绑定的内容写在原版临时创建的那条
 * {@link ServerInfo} 上：直接连接成功后原版会把它加进服务器列表，绑定也就跟着存进
 * {@code servers.dat}；如果这个地址本来就在列表里，则在下面前一步把绑定同步过去。
 *
 * <p>坑在这里：原版每次点「直接连接」都 <b>新建一个空条目</b>，所以界面关掉再打开时
 * 绑定会丢（地址框里的 IP 只是 {@code options.lastServer} 的文字）。为了不出现
 * “界面上写着未绑定、点连接却照样敲门”，模组额外按地址记了一份绑定
 * （{@link DirectBindings}，存在 {@code config/silent_knock_direct.json}），
 * 打开界面或改地址时按地址重新填回来；地址在服务器列表里有一条时，
 * 以那条为准——原版按下「加入服务器」后连的就是它。
 *
 * <p>已经绑定了密钥时，这一行中间会多出一个“解绑”小按钮，和“添加服务器”界面里的那个
 * 完全一样：点一下就把这个地址和密钥文件的绑定去掉，<b>文件本身还留在电脑里</b>，
 * 别的服务器还能接着用。
 */
@Mixin(DirectConnectScreen.class)
public abstract class DirectConnectScreenMixin extends Screen {
    /** 密钥按钮 + 端口框最小要占的宽度，地址框特别窄时兜底用。 */
    private static final int MIN_ROW_WIDTH = 140;

    @Shadow
    private ServerInfo serverEntry;

    @Shadow
    private TextFieldWidget addressField;

    @Shadow
    private Screen parent;

    @Unique
    private ButtonWidget silentspa$keyButton;

    @Unique
    private ButtonWidget silentspa$unbindButton;

    @Unique
    private TextFieldWidget silentspa$portField;

    /** 去选文件再回来时，把玩家刚输入的地址填回去。 */
    @Unique
    private String silentspa$draftAddress;

    /** 当前条目上这份绑定属于哪个地址；换了地址时要靠它判断能不能沿用。 */
    @Unique
    private String silentspa$boundAddress;

    /** 地址框还空着时先选好的密钥，等地址填上再认到那个地址名下。 */
    @Unique
    private String silentspa$pendingKey;

    protected DirectConnectScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void silentspa$resetWidgets(CallbackInfo ci) {
        // 重新 init（例如从文件选择器回来）时先放开旧控件：原版 init 结尾会触发一次
        // onAddressFieldChanged，那时新控件还没建好，别让回调去碰已经过期的控件
        this.silentspa$keyButton = null;
        this.silentspa$unbindButton = null;
        this.silentspa$portField = null;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void silentspa$addWidgets(CallbackInfo ci) {
        if (this.serverEntry == null || this.addressField == null) {
            return;
        }
        // 原版 init 会用 options.lastServer 覆盖地址框，这里把草稿填回去
        if (this.silentspa$draftAddress != null) {
            this.addressField.setText(this.silentspa$draftAddress);
            this.silentspa$draftAddress = null;
        }
        // 界面关掉再打开时，原版给的是一个全新的空条目；按地址把上次的绑定找回来
        this.silentspa$restoreBinding();

        SpaHooks hooks = (SpaHooks) (Object) this.serverEntry;
        // 位置从原版地址框算出来：紧贴它下面一行。
        // 原版 1.21.8 里地址框固定在 y=116（高 20），标题“Server Address”画在 y=100，
        // “加入服务器”按钮在 height/4+108；所以中间这段空隙正好放一行控件。
        int x = this.addressField.getX();
        int fieldBottom = this.addressField.getY() + this.addressField.getHeight();
        int joinTop = this.height / 4 + 108;
        int y = Math.min(fieldBottom + 4, joinTop - 22);
        y = Math.max(y, fieldBottom + 2);

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

        int total = this.silentspa$rowWidth();
        this.silentspa$portField = new TextFieldWidget(this.textRenderer,
                SpaKeyRow.portX(x, total), y, SpaKeyRow.PORT_WIDTH, 20,
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

    /**
     * 地址框里的文字一变，就按新地址重新找一次绑定。
     *
     * <p>原版这个回调只负责判断「加入服务器」按钮能不能点；我们在它跑完之后补一句，
     * 让密钥、端口跟着地址走——换了地址就不该再显示上一个服务器的密钥。
     */
    @Inject(method = "onAddressFieldChanged", at = @At("TAIL"))
    private void silentspa$onAddressChanged(CallbackInfo ci) {
        if (this.silentspa$portField == null || this.silentspa$keyButton == null) {
            // 我们的控件还没建好（原版 init 里会触发一次），交给 init 收尾
            return;
        }
        this.silentspa$restoreBinding();
        this.silentspa$syncWidgets();
    }

    /**
     * 按地址框里的地址把绑定“找回来”。
     *
     * <p>原版每次点「直接连接」都新建一个空的服务器条目，界面关掉再进来绑定就没了
     * （地址框里的 IP 只是 {@code options.lastServer} 的文字），所以这里按地址重新查：
     * 先看服务器列表里同地址的那条（原版连接时用的就是它），再看模组按地址记下来的那份。
     */
    @Unique
    private void silentspa$restoreBinding() {
        if (this.serverEntry == null || this.addressField == null) {
            return;
        }
        SpaHooks hooks = (SpaHooks) (Object) this.serverEntry;
        String address = this.addressField.getText();
        String key = null;
        int port = -1;
        ServerInfo listed = this.silentspa$listedEntry(address);
        if (listed != null) {
            SpaHooks other = (SpaHooks) (Object) listed;
            if (SpaKeyRow.isBound(other.silentspa$getKeyPath())) {
                key = other.silentspa$getKeyPath();
                port = other.silentspa$getSpaPort();
            }
        }
        if (!SpaKeyRow.isBound(key)) {
            DirectBindings.Binding remembered = DirectBindings.get(address);
            if (remembered != null) {
                key = remembered.keyPath;
                port = remembered.port;
            }
        }
        if (!SpaKeyRow.isBound(key)) {
            // 这个地址没有任何记录
            if (this.silentspa$pendingKey != null) {
                // 玩家先选了密钥、之后才填地址：把刚选的这份认到当前地址上
                hooks.silentspa$setKeyPath(this.silentspa$pendingKey);
                DirectBindings.put(address, this.silentspa$pendingKey, hooks.silentspa$getSpaPort());
                this.silentspa$pendingKey = null;
                this.silentspa$boundAddress = address;
                SilentSpaClient.LOGGER.debug("[SPA] 直连地址“{}”用上了刚选的密钥", address);
                return;
            }
            if (SpaKeyRow.isBound(hooks.silentspa$getKeyPath())
                    && this.silentspa$boundAddress != null
                    && !this.silentspa$boundAddress.equals(address)) {
                // 条目上留着的是「别的地址」的绑定：换了地址就清掉，免得张冠李戴
                hooks.silentspa$setKeyPath(null);
                hooks.silentspa$setSpaPort(-1);
                this.silentspa$boundAddress = null;
            }
            return;
        }
        if (!Objects.equals(key, hooks.silentspa$getKeyPath()) || port != hooks.silentspa$getSpaPort()) {
            hooks.silentspa$setKeyPath(key);
            hooks.silentspa$setSpaPort(port);
            SilentSpaClient.LOGGER.debug("[SPA] 直连地址“{}”的绑定已恢复：{}", address, key);
        }
        this.silentspa$boundAddress = address;
    }

    /** 把当前绑定同步回界面：端口框文字 + 密钥按钮 /「解绑」按钮的显隐。 */
    @Unique
    private void silentspa$syncWidgets() {
        if (this.serverEntry == null) {
            return;
        }
        SpaHooks hooks = (SpaHooks) (Object) this.serverEntry;
        if (this.silentspa$portField != null) {
            int port = hooks.silentspa$getSpaPort();
            String text = port > 0 ? Integer.toString(port) : "";
            if (!text.equals(this.silentspa$portField.getText())) {
                this.silentspa$portField.setText(text);
            }
        }
        this.silentspa$applyLayout(hooks);
    }

    /** 服务器列表本体；不在多人游戏界面后面时返回 null。 */
    @Unique
    private ServerList silentspa$list() {
        if (!(this.parent instanceof MultiplayerScreen screen)) {
            return null;
        }
        return screen.getServerList();
    }

    /**
     * 服务器列表里和这个地址一模一样的那条。
     *
     * <p>原版按下「加入服务器」时会先按地址查列表，查到就连接<b>列表里那条</b>、
     * 把当前这个临时条目丢掉，所以绑定能不能生效看的是它。
     */
    @Unique
    private ServerInfo silentspa$listedEntry(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        ServerList list = this.silentspa$list();
        if (list == null) {
            return null;
        }
        ServerInfo listed = list.get(address);
        return listed == null || listed == this.serverEntry ? null : listed;
    }

    /** 把绑定写到服务器列表里同地址的那条上，并立刻存盘。 */
    @Unique
    private void silentspa$writeToList(String address, String key, int port) {
        ServerInfo listed = this.silentspa$listedEntry(address);
        if (listed == null) {
            return;
        }
        SpaHooks to = (SpaHooks) (Object) listed;
        if (Objects.equals(key, to.silentspa$getKeyPath()) && port == to.silentspa$getSpaPort()) {
            return;
        }
        to.silentspa$setKeyPath(key);
        to.silentspa$setSpaPort(port);
        ServerList list = this.silentspa$list();
        if (list != null) {
            list.saveFile();
        }
        SilentSpaClient.LOGGER.info("[SPA] 已把密钥绑定同步到服务器列表里的条目：{}", listed.address);
    }

    /** 这一行能用的宽度：跟着原版地址框走（原版一般是 200）。 */
    @Unique
    private int silentspa$rowWidth() {
        int width = this.addressField == null ? 0 : this.addressField.getWidth();
        return Math.max(MIN_ROW_WIDTH, width);
    }

    /** 按“有没有绑定”重新摆这一行控件：解绑按钮只在绑定了的时候出现。 */
    @Unique
    private void silentspa$applyLayout(SpaHooks hooks) {
        if (this.silentspa$keyButton == null || this.silentspa$unbindButton == null
                || this.silentspa$portField == null || this.addressField == null) {
            return;
        }
        boolean bound = SpaKeyRow.isBound(hooks.silentspa$getKeyPath());
        Text unbindLabel = Text.translatable("silent_knock.button.unbind");
        int unbindWidth = bound ? SpaKeyRow.unbindWidth(this.textRenderer, unbindLabel) : 0;
        int x = this.addressField.getX();
        int total = this.silentspa$rowWidth();
        int keyWidth = SpaKeyRow.keyWidth(total, unbindWidth);
        this.silentspa$keyButton.setWidth(keyWidth);
        this.silentspa$keyButton.setMessage(SpaKeyRow.label(this.textRenderer,
                hooks.silentspa$getKeyPath(), keyWidth));
        this.silentspa$keyButton.setTooltip(SpaKeyRow.tooltip(hooks.silentspa$getKeyPath()));
        this.silentspa$unbindButton.setMessage(unbindLabel);
        this.silentspa$unbindButton.setWidth(Math.max(SpaKeyRow.UNBIND_WIDTH, unbindWidth));
        this.silentspa$unbindButton.setX(SpaKeyRow.unbindX(x, total, unbindWidth));
        this.silentspa$unbindButton.visible = bound;
        this.silentspa$unbindButton.active = bound;
        this.silentspa$portField.setX(SpaKeyRow.portX(x, total));
    }

    /** 点“解绑”：只清掉“这个地址用哪份密钥”，密钥文件本身留在电脑里。 */
    @Unique
    private void silentspa$unbind() {
        SpaHooks hooks = (SpaHooks) (Object) this.serverEntry;
        String path = hooks.silentspa$getKeyPath();
        if (!SpaKeyRow.isBound(path)) {
            return;
        }
        String fileName = SpaKeyRow.fileName(path);
        String address = this.addressField == null ? "" : this.addressField.getText();
        hooks.silentspa$setKeyPath(null);
        hooks.silentspa$setSpaPort(-1);
        this.silentspa$boundAddress = null;
        this.silentspa$pendingKey = null;
        if (this.silentspa$portField != null) {
            this.silentspa$portField.setText("");
        }
        // 按地址记下的那份、以及服务器列表里同地址的那条，都要一起解绑，
        // 不然下次打开这个地址又会被填回来，点连接也照样敲门
        DirectBindings.remove(address);
        this.silentspa$writeToList(address, null, -1);
        this.silentspa$applyLayout(hooks);
        String label = address.strip();
        if (label.isEmpty()) {
            label = fileName;
        }
        SilentSpaClient.LOGGER.info("[SPA] 已解除直接连接地址“{}”的密钥绑定（{}，文件保留）", label, fileName);
        SpaFeedback.keyUnbound(label);
    }

    @Unique
    private void silentspa$openPicker() {
        this.silentspa$draftAddress = this.addressField.getText();
        this.client.setScreen(new SpaFilePickerScreen((Screen) (Object) this, this.addressField.getText(), path -> {
            String address = this.addressField == null ? "" : this.addressField.getText();
            SpaHooks hooks = (SpaHooks) (Object) this.serverEntry;
            hooks.silentspa$setKeyPath(path.toString());
            this.silentspa$boundAddress = address;
            if (address.isBlank()) {
                // 地址还没填：先记着，等地址填上再落到那个地址名下
                this.silentspa$pendingKey = path.toString();
            } else {
                // 立刻按地址记下来：这样 ESC 回列表再进来能看到绑定，而不是写着“未绑定”却照样敲门
                DirectBindings.put(address, path.toString(), hooks.silentspa$getSpaPort());
                this.silentspa$writeToList(address, path.toString(), hooks.silentspa$getSpaPort());
            }
            SilentSpaClient.LOGGER.info("[SPA] 已为直接连接地址“{}”绑定密钥文件：{}", address, path);
            SpaManager.validateKeyFile(path);
        }));
    }

    /**
     * 原版按下“加入服务器”后会先按地址在服务器列表里查有没有同地址的条目，
     * 有就连接那条、并把临时条目丢掉。所以这里在连接前把绑定同步到列表里那条上，
     * 免得玩家明明绑了密钥却还是没敲门。
     */
    @Inject(method = "saveAndClose", at = @At("HEAD"))
    private void silentspa$carryBindingToListEntry(CallbackInfo ci) {
        if (this.serverEntry == null || this.addressField == null) {
            return;
        }
        // 原版这时候还没把地址写进条目里，所以用地址框原文查，口径和原版保持一致
        SpaHooks from = (SpaHooks) (Object) this.serverEntry;
        this.silentspa$writeToList(this.addressField.getText(),
                from.silentspa$getKeyPath(), from.silentspa$getSpaPort());
    }
}
