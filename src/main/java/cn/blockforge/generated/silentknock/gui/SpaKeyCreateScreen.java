package cn.blockforge.generated.silentknock.gui;

import cn.blockforge.generated.silentknock.config.SpaConfig;
import cn.blockforge.generated.silentknock.logic.ExtraKnock;
import cn.blockforge.generated.silentknock.logic.SpaFeedback;
import cn.blockforge.generated.silentknock.spa.FwknopRc;
import cn.blockforge.generated.silentknock.spa.SpaKeyException;
import cn.blockforge.generated.silentknock.spa.SpaKeyGenerator;
import cn.blockforge.generated.silentknock.spa.SpaKeyMaterial;
import cn.blockforge.generated.silentknock.spa.SpaKeyVault;
import cn.blockforge.generated.silentknock.spa.SpaProfile;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 新建一份 SPA 配置（fwknoprc）的界面。
 *
 * <p>玩家手上已经有一对 Base64 密钥（或者让模组现场生成），照着 fwknop-gui 里那些选项填一下，
 * 就能得到一份标准 fwknoprc 文件：明文保存，自己留着用、拷给别的电脑、或者给 fwknop 客户端
 * 用都行，服务器那边把同样的两把密钥写进 fwknopd 就成。
 *
 * <p>只保留真正影响敲门的选项：两把密钥、摘要算法、HMAC 算法、ALLOW_IP、敲门端口。
 * “Key Is Base 64 / HMAC Is Base 64” 默认就是勾上的，所以省掉；“Message Type” 固定是打开端口；
 * 推送 GPG 的模式本模组不支持，也省掉。
 *
 * <p>访问端口（{@code ACCESS}）不让人填：敲门要打开的正是这次要连的那个端口，
 * 所以直接按地址框里的端口写成 {@code tcp/25565}。语音模组之类的额外端口也
 * <b>不用</b>写进来——装了语音模组时模组敲门会自动带上它的默认端口，还缺别的端口
 * 就在游戏里按 ESC →「SPA 敲门」临时补敲一次，这份文件不用改。
 */
public final class SpaKeyCreateScreen extends Screen {
    /** 和 fwknop 的 DIGEST_TYPE / HMAC_DIGEST_TYPE 取值一致。 */
    private static final String[] DIGEST_TYPES = {"sha256", "sha1", "sha384", "sha512", "md5"};
    private static final int LABEL_COLOR = 0xFFAAAAAA;
    private static final int HINT_COLOR = 0xFF808080;
    private static final int OK_COLOR = 0xFF7CFC00;
    private static final int ERROR_COLOR = 0xFFFF6B6B;

    /** 标签和控件之间的间距（标签在下，控件在上）。 */
    private static final int LABEL_GAP = 9;
    private static final int FIELD_HEIGHT = 18;
    private static final int ROWS = 6;
    /** 标题和顶部说明的行高（字高 9 再加 1 像素行距）。 */
    private static final int HEADER_LINE = 10;
    /** 滚一下鼠标滚轮走多少像素。 */
    private static final int SCROLL_STEP = 18;

    /** 保存成功后要回到的界面（原版的添加服务器 / 直接连接界面）。 */
    private final Screen parentScreen;
    /** 点“取消”时回到的界面（模组自己的文件选择器）。 */
    private final Screen pickerScreen;
    /** 地址框里的服务器地址，用来当默认文件名、SPA_SERVER 和访问端口。 */
    private final String suggestedServer;
    private final Consumer<Path> onPick;

    // 这些状态存在字段里：窗口缩放时 Screen 会清空控件重跑 init()
    private String nameText;
    private String keyText;
    private String hmacText;
    private String portText;
    private String allowIpText;
    private int digestIndex;
    private int hmacIndex;
    private int allowMode;

    private TextFieldWidget nameField;
    private TextFieldWidget keyField;
    private TextFieldWidget hmacField;
    private TextFieldWidget allowIpField;
    private TextFieldWidget portField;
    private ButtonWidget digestButton;
    private ButtonWidget hmacButton;
    private ButtonWidget allowButton;

    /** “访问端口（自动）”那一格：只显示，不让人填。 */
    private String autoAccessText;
    private int autoAccessX;
    private int autoAccessY;

    /** 底部那几行提示，拼一次就够。 */
    private final List<Text> hints;

    private final List<Label> labels = new ArrayList<>();
    /** 所有会跟着滚动的控件，连同它没滚动时该在的 y。 */
    private final List<ScrollItem> scrollItems = new ArrayList<>();
    /** 钉在屏幕下沿的按钮：不跟着滚，也不吃滚动区的裁剪。 */
    private final List<ClickableWidget> fixedButtons = new ArrayList<>();

    private int left;
    private int contentWidth;
    private int top;
    private int rowHeight;

    /** 顶部标题和“明文保存…”那行说明，按窗口宽度折好行的结果。 */
    private List<OrderedText> titleLines = List.of();
    private List<OrderedText> storageLines = List.of();
    /** 头部占掉的高度：滚动内容从这条线往下才开始，免得滑到标题上。 */
    private int viewTop;

    /** 提示区（状态文字 + 几行说明）没滚动时从哪儿开始。 */
    private int hintTop;
    /** 现在滚到哪儿了（像素）。 */
    private int scroll;
    /** 最多能滚多少（内容超出可视区的部分）。 */
    private int maxScroll;
    /** 可视区的下沿：底部按钮上面一点。 */
    private int viewBottom;
    /** 右边那根滚动条的位置。 */
    private int scrollBarX;

    private Text status;
    private boolean statusError;

    private record Label(Text text, int x, int baseY) {
    }

    private record ScrollItem(ClickableWidget widget, int baseY) {
    }

    public SpaKeyCreateScreen(Screen parentScreen, Screen pickerScreen, String suggestedServer,
                              Consumer<Path> onPick) {
        super(Text.translatable("silent_knock.create.title"));
        this.parentScreen = parentScreen;
        this.pickerScreen = pickerScreen;
        this.suggestedServer = suggestedServer == null ? "" : suggestedServer;
        this.onPick = onPick;
        this.nameText = this.silentspa$defaultFileName();
        SpaKeyGenerator.Keys keys = SpaKeyGenerator.generate();
        this.keyText = keys.encryptionBase64;
        this.hmacText = keys.hmacBase64;
        this.portText = Integer.toString(SpaConfig.get().spaPort);
        this.allowIpText = "0.0.0.0";
        this.autoAccessText = this.silentspa$autoAccessText();
        this.hints = silentspa$hintText();
    }

    /** 底部提示：先说“只填密钥就行”，再解释访问端口是自动的、GPG 不支持。 */
    private static List<Text> silentspa$hintText() {
        return List.of(
                Text.translatable("silent_knock.create.hint"),
                Text.translatable("silent_knock.create.hint.access"),
                Text.translatable("silent_knock.create.hint.gpg"));
    }

    // ------------------------------------------------------------------ 布局

    @Override
    protected void init() {
        this.silentspa$capture();
        this.clearChildren();
        this.labels.clear();
        this.scrollItems.clear();
        this.fixedButtons.clear();

        this.contentWidth = Math.min(360, Math.max(200, this.width - 24));
        this.left = (this.width - this.contentWidth) / 2;
        this.scrollBarX = this.left + this.contentWidth + 5;
        int buttonsY = this.height - 26;
        // 行高固定 27：装不下就让整页滚动，不再把行压扁（压扁中文会被切掉半行）
        this.rowHeight = 27;
        // 顶部标题和说明按窗口宽度折行：窗口窄的时候硬画一行会被左右两边切掉
        int headerWidth = Math.max(80, this.width - 16);
        this.titleLines = this.textRenderer.wrapLines(this.title, headerWidth);
        this.storageLines = this.textRenderer.wrapLines(
                Text.translatable("silent_knock.create.storage"), headerWidth);
        this.viewTop = 6 + (this.titleLines.size() + this.storageLines.size()) * HEADER_LINE + 1;
        // 窗口矮到没地方时也别让头部吃掉整个可视区
        this.viewTop = Math.min(this.viewTop, Math.max(12, buttonsY - 30));
        this.top = this.viewTop + 2;
        // 窗口特别矮时也要留出一像素的可视区，否则裁剪框会翻过来
        this.viewBottom = Math.max(this.top + 1, buttonsY - 4);
        this.hintTop = this.top + ROWS * this.rowHeight + 6;

        int fullWidth = this.contentWidth;
        int columnWidth = (this.contentWidth - 6) / 2;

        this.nameField = this.silentspa$textField(0, this.left, fullWidth, 64,
                "silent_knock.create.name", "silent_knock.create.name.tooltip");
        this.keyField = this.silentspa$textField(1, this.left, fullWidth, 128,
                "silent_knock.create.key.label", "silent_knock.create.key.tooltip");
        this.hmacField = this.silentspa$textField(2, this.left, fullWidth, 256,
                "silent_knock.create.hmac.label", "silent_knock.create.hmac.tooltip");

        this.digestButton = this.silentspa$optionButton(3, this.left, columnWidth,
                "silent_knock.create.digest", this.silentspa$digestLabel(), () -> {
                    this.digestIndex = (this.digestIndex + 1) % DIGEST_TYPES.length;
                    this.digestButton.setMessage(this.silentspa$digestLabel());
                });
        this.hmacButton = this.silentspa$optionButton(3, this.left + columnWidth + 6, columnWidth,
                "silent_knock.create.hmac_type", this.silentspa$hmacLabel(), () -> {
                    this.hmacIndex = (this.hmacIndex + 1) % DIGEST_TYPES.length;
                    this.hmacButton.setMessage(this.silentspa$hmacLabel());
                });

        this.allowButton = this.silentspa$optionButton(4, this.left, columnWidth,
                "silent_knock.create.allow_ip", this.silentspa$allowLabel(), () -> {
                    this.allowMode = this.allowMode == 0 ? 1 : 0;
                    this.allowButton.setMessage(this.silentspa$allowLabel());
                    this.silentspa$syncAllowIpField();
                });
        this.allowIpField = this.silentspa$textField(4, this.left + columnWidth + 6, columnWidth, 45,
                "silent_knock.create.allow_ip.value", "silent_knock.create.allow_ip.tooltip");
        this.allowIpField.setText(this.allowIpText);

        this.portField = this.silentspa$textField(5, this.left, columnWidth, 5,
                "silent_knock.create.port", "silent_knock.create.port.tooltip");
        this.portField.setText(this.portText);
        // 访问端口不让填：敲门要开的就是这次要连的端口，这里只把算出来的值显示出来
        this.autoAccessX = this.left + columnWidth + 6;
        this.autoAccessY = this.silentspa$rowY(5);
        this.autoAccessText = this.silentspa$autoAccessText();
        this.labels.add(new Label(Text.translatable("silent_knock.create.access"),
                this.autoAccessX, this.autoAccessY));

        int gap = 4;
        int buttonWidth = (this.contentWidth - gap * 3) / 4;
        this.silentspa$fixedButton(ButtonWidget.builder(Text.translatable("silent_knock.create.random"),
                        button -> this.silentspa$regenerate())
                .dimensions(this.left, buttonsY, buttonWidth, 20)
                .tooltip(Tooltip.of(Text.translatable("silent_knock.create.random.tooltip")))
                .build());
        this.silentspa$fixedButton(ButtonWidget.builder(Text.translatable("silent_knock.create.copy"),
                        button -> this.silentspa$copy())
                .dimensions(this.left + (buttonWidth + gap), buttonsY, buttonWidth, 20)
                .tooltip(Tooltip.of(Text.translatable("silent_knock.create.copy.tooltip")))
                .build());
        this.silentspa$fixedButton(ButtonWidget.builder(Text.translatable("silent_knock.create.save"),
                        button -> this.silentspa$save())
                .dimensions(this.left + (buttonWidth + gap) * 2, buttonsY, buttonWidth, 20)
                .tooltip(Tooltip.of(Text.translatable("silent_knock.create.save.tooltip")))
                .build());
        this.silentspa$fixedButton(ButtonWidget.builder(Text.translatable("silent_knock.create.cancel"),
                        button -> this.close())
                .dimensions(this.left + (buttonWidth + gap) * 3, buttonsY, buttonWidth, 20)
                .build());

        this.silentspa$syncAllowIpField();
        this.silentspa$restore();
        this.silentspa$updateScrollBounds();
        this.silentspa$applyScroll();
    }

    private int silentspa$rowY(int row) {
        return this.top + row * this.rowHeight;
    }

    private TextFieldWidget silentspa$textField(int row, int x, int width, int maxLength,
                                                String labelKey, String tooltipKey) {
        int baseY = this.silentspa$rowY(row) + LABEL_GAP;
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, x, baseY - this.scroll, width,
                FIELD_HEIGHT, Text.translatable(labelKey));
        field.setMaxLength(maxLength);
        field.setTooltip(Tooltip.of(Text.translatable(tooltipKey)));
        this.addDrawableChild(field);
        this.scrollItems.add(new ScrollItem(field, baseY));
        this.labels.add(new Label(Text.translatable(labelKey), x, this.silentspa$rowY(row)));
        return field;
    }

    private ButtonWidget silentspa$optionButton(int row, int x, int width, String labelKey,
                                               Text value, Runnable onPress) {
        int baseY = this.silentspa$rowY(row) + LABEL_GAP;
        ButtonWidget button = ButtonWidget.builder(value, b -> onPress.run())
                .dimensions(x, baseY - this.scroll, width, FIELD_HEIGHT)
                .build();
        this.addDrawableChild(button);
        this.scrollItems.add(new ScrollItem(button, baseY));
        this.labels.add(new Label(Text.translatable(labelKey), x, this.silentspa$rowY(row)));
        return button;
    }

    /** 底部那排按钮：加进控件表能点，另外记一笔，画的时候不跟内容一起裁剪。 */
    private void silentspa$fixedButton(ButtonWidget button) {
        this.addDrawableChild(button);
        this.fixedButtons.add(button);
    }

    // ------------------------------------------------------------------ 滚动

    /** 内容加起来有多高：6 行控件 + 提示区（状态文字会变高，所以每次都重算）。 */
    private int silentspa$contentHeight() {
        int height = this.hintTop - this.top;
        if (this.status != null) {
            height += this.textRenderer.getWrappedLinesHeight(this.status, this.contentWidth) + 4;
        }
        for (Text hint : this.hints) {
            height += this.textRenderer.getWrappedLinesHeight(hint, this.contentWidth) + 5;
        }
        return height;
    }

    /** 重新算还能滚多远；窗口变高、窗口缩放、状态文字变长都会走到这儿。 */
    private void silentspa$updateScrollBounds() {
        int available = this.viewBottom - this.top;
        this.maxScroll = Math.max(0, this.silentspa$contentHeight() - available);
        this.scroll = Math.min(Math.max(this.scroll, 0), this.maxScroll);
    }

    /** 把滚动量摊到每个控件上；滑出可视区的控件连点击一起关掉。 */
    private void silentspa$applyScroll() {
        for (ScrollItem item : this.scrollItems) {
            ClickableWidget widget = item.widget();
            int y = item.baseY() - this.scroll;
            widget.setY(y);
            // 只遮挡不关点击的话，点标题那一片会误点到滚上去了的输入框
            widget.visible = y + widget.getHeight() > this.viewTop && y < this.viewBottom;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount,
                                 double verticalAmount) {
        if (this.maxScroll <= 0) {
            return false;
        }
        int next = this.scroll - (int) Math.round(verticalAmount * SCROLL_STEP);
        next = Math.min(Math.max(next, 0), this.maxScroll);
        if (next == this.scroll) {
            return false;
        }
        this.scroll = next;
        this.silentspa$applyScroll();
        return true;
    }

    private Text silentspa$digestLabel() {
        return Text.literal(DIGEST_TYPES[this.digestIndex].toUpperCase(Locale.ROOT));
    }

    private Text silentspa$hmacLabel() {
        return Text.literal(DIGEST_TYPES[this.hmacIndex].toUpperCase(Locale.ROOT));
    }

    private Text silentspa$allowLabel() {
        return Text.translatable(this.allowMode == 0
                ? "silent_knock.create.allow_ip.source"
                : "silent_knock.create.allow_ip.custom");
    }

    private void silentspa$syncAllowIpField() {
        if (this.allowIpField == null) {
            return;
        }
        boolean custom = this.allowMode == 1;
        this.allowIpField.setEditable(custom);
        if (!custom) {
            this.allowIpText = "0.0.0.0";
            this.allowIpField.setText(this.allowIpText);
        }
    }

    private void silentspa$capture() {
        if (this.nameField != null) {
            this.nameText = this.nameField.getText();
        }
        if (this.keyField != null) {
            this.keyText = this.keyField.getText();
        }
        if (this.hmacField != null) {
            this.hmacText = this.hmacField.getText();
        }
        if (this.portField != null) {
            this.portText = this.portField.getText();
        }
        if (this.allowIpField != null) {
            this.allowIpText = this.allowIpField.getText();
        }
    }

    private void silentspa$restore() {
        this.nameField.setText(this.nameText);
        this.keyField.setText(this.keyText);
        this.hmacField.setText(this.hmacText);
        this.portField.setText(this.portText);
        this.allowIpField.setText(this.allowIpText);
        this.digestButton.setMessage(this.silentspa$digestLabel());
        this.hmacButton.setMessage(this.silentspa$hmacLabel());
        this.allowButton.setMessage(this.silentspa$allowLabel());
    }

    // ------------------------------------------------------------------ 数据

    /** 输入框里的服务器地址去掉端口部分。 */
    private String silentspa$hostOnly() {
        String raw = this.suggestedServer.strip();
        if (raw.startsWith("[")) {
            int end = raw.indexOf(']');
            if (end > 0) {
                return raw.substring(1, end);
            }
        }
        int colon = raw.lastIndexOf(':');
        if (colon > 0 && raw.indexOf(':') == colon) {
            raw = raw.substring(0, colon);
        }
        return raw.strip();
    }

    /** 这次要连的端口（地址里写了就用地址里的）。 */
    private int silentspa$targetPort() {
        String raw = this.suggestedServer.strip();
        int colon = raw.lastIndexOf(':');
        if (colon > 0 && raw.indexOf(':') == colon) {
            try {
                int port = Integer.parseInt(raw.substring(colon + 1).strip());
                if (port >= 1 && port <= 65535) {
                    return port;
                }
            } catch (NumberFormatException ignored) {
                // 地址里没写端口
            }
        }
        return 25565;
    }

    private String silentspa$defaultFileName() {
        String host = this.silentspa$hostOnly().replaceAll("[^A-Za-z0-9._-]", "_");
        if (host.isBlank()) {
            return "spa-config";
        }
        if (host.length() > 40) {
            host = host.substring(0, 40);
        }
        return host;
    }

    private void silentspa$regenerate() {
        SpaKeyGenerator.Keys keys = SpaKeyGenerator.generate();
        this.keyText = keys.encryptionBase64;
        this.hmacText = keys.hmacBase64;
        this.keyField.setText(this.keyText);
        this.hmacField.setText(this.hmacText);
        this.silentspa$setStatus(Text.translatable("silent_knock.create.regenerated"), false);
    }

    /**
     * 把界面上的输入拼成一份配置。
     *
     * @return 配置；输入有问题时返回 null，并把错误写进状态栏
     */
    /** 「访问端口（自动）」那一格显示什么：游戏端口 + 自动带上的语音端口。 */
    private String silentspa$autoAccessText() {
        List<String> entries = FwknopRc.splitAccess("tcp/" + this.silentspa$targetPort());
        for (String extra : ExtraKnock.autoAccessEntries(this.silentspa$targetPort())) {
            String entry = FwknopRc.normalizeAccessEntry(extra);
            if (entry != null && !entries.contains(entry)) {
                entries.add(entry);
            }
        }
        return FwknopRc.joinAccess(entries);
    }

    private SpaProfile silentspa$buildProfile() {
        int port;
        try {
            port = Integer.parseInt(this.portField.getText().strip());
        } catch (NumberFormatException e) {
            port = -1;
        }
        if (port < 1 || port > 65535) {
            this.silentspa$setStatus(Text.translatable("silent_knock.create.invalid_port"), true);
            return null;
        }

        // 访问端口不用填：敲的就是这次要连的那个端口（地址框里的端口）
        List<String> accessEntries = FwknopRc.splitAccess("tcp/" + this.silentspa$targetPort());
        if (accessEntries.isEmpty()) {
            accessEntries = List.of("tcp/25565");
        }

        byte[] encryption;
        byte[] hmac;
        try {
            encryption = FwknopRc.decodeBase64(this.keyField.getText().strip(),
                    Text.translatable("silent_knock.create.key.label").getString());
            hmac = FwknopRc.decodeBase64(this.hmacField.getText().strip(),
                    Text.translatable("silent_knock.create.hmac.label").getString());
        } catch (SpaKeyException e) {
            this.silentspa$setStatus(e.getText(), true);
            return null;
        }
        if (encryption.length < 1 || encryption.length > 64) {
            this.silentspa$setStatus(Text.translatable("silent_knock.key.bad_encryption_length",
                    Integer.toString(encryption.length)), true);
            return null;
        }
        if (hmac.length < 1 || hmac.length > 256) {
            this.silentspa$setStatus(Text.translatable("silent_knock.key.bad_hmac_length",
                    Integer.toString(hmac.length)), true);
            return null;
        }

        String allowIp = this.allowMode == 0 ? SpaProfile.ALLOW_IP_SOURCE : this.allowIpField.getText().strip();
        if (allowIp.isEmpty()) {
            allowIp = SpaProfile.ALLOW_IP_SOURCE;
        }
        return new SpaProfile(
                SpaKeyVault.sectionName(this.nameField.getText()),
                this.silentspa$hostOnly(),
                port,
                "udp",
                new SpaKeyMaterial(encryption, hmac),
                DIGEST_TYPES[this.digestIndex],
                DIGEST_TYPES[this.hmacIndex],
                allowIp,
                SpaProfile.MSG_TYPE_OPEN_PORT,
                FwknopRc.joinAccess(accessEntries));
    }

    private void silentspa$copy() {
        SpaProfile profile = this.silentspa$buildProfile();
        if (profile == null || this.client == null) {
            return;
        }
        this.client.keyboard.setClipboard(FwknopRc.render(profile));
        this.silentspa$setStatus(Text.translatable("silent_knock.create.copied"), false);
    }

    private void silentspa$save() {
        SpaProfile profile = this.silentspa$buildProfile();
        if (profile == null) {
            return;
        }
        try {
            Path path = SpaKeyVault.write(this.nameField.getText(), FwknopRc.render(profile));

            SpaConfig config = SpaConfig.get();
            Path directory = path.getParent();
            if (directory != null) {
                config.lastKeyDir = directory.toString();
                config.save();
            }

            Path fileName = path.getFileName();
            SpaFeedback.keyCreated(fileName == null ? path.toString() : fileName.toString());
            this.onPick.accept(path.toAbsolutePath().normalize());
            if (this.client != null) {
                this.client.setScreen(this.parentScreen);
            }
        } catch (SpaKeyException e) {
            this.silentspa$setStatus(e.getText(), true);
        } catch (RuntimeException e) {
            this.silentspa$setStatus(Text.translatable("silent_knock.create.failed",
                    String.valueOf(e.getMessage())), true);
        }
    }

    private void silentspa$setStatus(Text text, boolean error) {
        this.status = text;
        this.statusError = error;
    }

    // ------------------------------------------------------------------ 画面

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.pickerScreen);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 内容高度会跟着状态文字和窗口大小变，画之前先把能滚多远校一遍
        this.silentspa$updateScrollBounds();
        this.silentspa$applyScroll();

        int centerX = this.width / 2;
        // 标题和说明都是一行行画：上面已经按窗口宽度折好了，不会再被左右切掉
        int headerY = 6;
        for (OrderedText line : this.titleLines) {
            context.drawCenteredTextWithShadow(this.textRenderer, line, centerX, headerY, 0xFFFFFFFF);
            headerY += HEADER_LINE;
        }
        for (OrderedText line : this.storageLines) {
            context.drawCenteredTextWithShadow(this.textRenderer, line, centerX, headerY, HINT_COLOR);
            headerY += HEADER_LINE;
        }
        if (this.maxScroll > 0) {
            // 内容比屏幕高时，右上角提一句可以用滚轮；和标题挤在一起就不画（右边有滚动条）
            Text tip = Text.translatable("silent_knock.create.scroll_hint");
            int tipX = this.left + this.contentWidth - this.textRenderer.getWidth(tip);
            int titleRight = centerX + this.textRenderer.getWidth(this.title) / 2;
            if (tipX > titleRight + 6) {
                context.drawTextWithShadow(this.textRenderer, tip, tipX, 6, HINT_COLOR);
            }
        }

        // 只有中间这块能看见：滚出去的内容和被按钮压住的内容都裁掉
        int clipBottom = Math.max(this.viewTop + 1, this.viewBottom);
        context.enableScissor(this.left - 4, this.viewTop, this.left + this.contentWidth + 4, clipBottom);

        // 只画会滚的那几行；底部按钮不在这儿画，免得被裁剪框切掉
        for (ScrollItem item : this.scrollItems) {
            item.widget().render(context, mouseX, mouseY, delta);
        }

        for (Label label : this.labels) {
            int y = label.baseY() - this.scroll;
            if (y + 9 < this.viewTop || y > clipBottom) {
                continue;
            }
            context.drawTextWithShadow(this.textRenderer, label.text(), label.x(), y, LABEL_COLOR);
        }
        if (this.autoAccessText != null) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(this.autoAccessText),
                    this.autoAccessX, this.autoAccessY + LABEL_GAP + 5 - this.scroll, OK_COLOR);
        }

        // 提示文字跟着一起滚，不再按“按钮上方那点地方”硬裁
        int y = this.hintTop - this.scroll;
        if (this.status != null) {
            context.drawWrappedTextWithShadow(this.textRenderer, this.status, this.left, y,
                    this.contentWidth, this.statusError ? ERROR_COLOR : OK_COLOR);
            y += this.textRenderer.getWrappedLinesHeight(this.status, this.contentWidth) + 4;
        }
        for (Text hint : this.hints) {
            context.drawWrappedTextWithShadow(this.textRenderer, hint, this.left, y,
                    this.contentWidth, HINT_COLOR);
            y += this.textRenderer.getWrappedLinesHeight(hint, this.contentWidth) + 5;
        }

        context.disableScissor();
        // 底部按钮钉在屏幕下沿，裁剪关了再画，滚到哪儿它们都在原地
        for (ClickableWidget button : this.fixedButtons) {
            button.render(context, mouseX, mouseY, delta);
        }
        this.silentspa$renderScrollBar(context);
    }

    /** 右边画一根细滚动条，让玩家知道下面还有内容。 */
    private void silentspa$renderScrollBar(DrawContext context) {
        if (this.maxScroll <= 0) {
            return;
        }
        int barTop = this.top;
        int barHeight = this.viewBottom - barTop;
        if (barHeight < 12) {
            return;
        }
        int x = this.scrollBarX;
        context.fill(x, barTop, x + 3, barTop + barHeight, 0x40FFFFFF);
        int thumbHeight = Math.max(14, barHeight * barHeight / (barHeight + this.maxScroll));
        int thumbY = barTop + (barHeight - thumbHeight) * this.scroll / this.maxScroll;
        context.fill(x, thumbY, x + 3, thumbY + thumbHeight, 0xCCFFFFFF);
    }
}
