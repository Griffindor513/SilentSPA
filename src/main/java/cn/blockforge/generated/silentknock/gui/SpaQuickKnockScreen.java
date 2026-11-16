package cn.blockforge.generated.silentknock.gui;

import cn.blockforge.generated.silentknock.SpaHooks;
import cn.blockforge.generated.silentknock.logic.ExtraKnock;
import cn.blockforge.generated.silentknock.logic.SpaManager;
import cn.blockforge.generated.silentknock.spa.FwknopRc;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 游戏里的小窗：“临时敲门”。
 *
 * <p>用来在需要的时候补敲一个端口（语音模组、网页地图之类），敲完门服务器会放行
 * 一小段时间（由服务器 fwknopd 的 {@code FW_ACCESS_TIMEOUT} 决定，默认约 30 秒），
 * <b>不会</b>写进任何配置文件。
 *
 * <p>输入框<b>不预填</b>：玩家想敲哪个口就自己填哪个（例如 {@code udp/24454}）。
 * 下面几个按钮是常见模组的示例，点一下就把端口填进输入框，省得记端口号。
 *
 * <p>入口在暂停菜单（ESC）里，只在当前服务器绑定了 SPA 密钥文件时才出现。
 */
public final class SpaQuickKnockScreen extends Screen {
    private static final int LABEL_COLOR = 0xFFA0A0A0;
    private static final int HINT_COLOR = 0xFF909090;
    private static final int ERROR_COLOR = 0xFFFF6B6B;
    private static final int OK_COLOR = 0xFF55FF55;
    private static final int TITLE_COLOR = 0xFFFFFFFF;

    /** 一个“示例”按钮：点一下把 {@code entry} 填进端口框。 */
    private record Preset(Text label, String entry) {
    }

    private final Screen parent;
    private final ServerInfo info;

    private TextFieldWidget portField;
    /** 窗口缩放时 Screen 会重建控件，把玩家已经输入的内容存这里。 */
    private String portText = "";
    private Text status;
    private boolean statusError;

    private int left;
    private int contentWidth;
    private int fieldY;
    private int statusY;

    /** 下面这一串是滚动用的：窗口矮（GUI 缩放调得大）时内容能滚，中文不会被切掉。 */
    private static final int SCROLL_STEP = 18;
    private final List<ScrollItem> scrollItems = new ArrayList<>();
    /** 钉在屏幕下沿的按钮：不跟着滚，也不吃滚动区的裁剪。 */
    private final List<ClickableWidget> fixedButtons = new ArrayList<>();
    private int viewTop;
    private int viewBottom;
    private int scrollBarX;
    private int scroll;
    private int maxScroll;

    private record ScrollItem(ClickableWidget widget, int baseY) {
    }

    public SpaQuickKnockScreen(Screen parent) {
        super(Text.translatable("silent_knock.knock.title"));
        this.parent = parent;
        MinecraftClient client = MinecraftClient.getInstance();
        this.info = client == null ? null : client.getCurrentServerEntry();
    }

    /** 当前服务器绑了密钥才值得打开这个界面。 */
    public static boolean isAvailable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return false;
        }
        ServerInfo info = client.getCurrentServerEntry();
        return info != null && SpaManager.hasKey(info);
    }

    private int silentspa$gamePort() {
        return this.info == null ? 25565 : SpaManager.serverPortOf(this.info);
    }

    /** 常见要额外端口的模组：只是“示例”，玩家点一下就把端口填进输入框。 */
    private List<Preset> silentspa$presets() {
        int gamePort = this.silentspa$gamePort();
        List<Preset> presets = new ArrayList<>();
        presets.add(new Preset(Text.translatable("silent_knock.knock.preset.voicechat"),
                "udp/" + ExtraKnock.VOICE_CHAT_DEFAULT_PORT));
        // Plasmo Voice 默认跟游戏端口共用，所以这里按当前服务器端口算
        presets.add(new Preset(Text.translatable("silent_knock.knock.preset.plasmovoice"),
                "udp/" + gamePort));
        presets.add(new Preset(Text.translatable("silent_knock.knock.preset.dynmap"),
                "tcp/" + ExtraKnock.WEB_MAP_DEFAULT_PORT));
        presets.add(new Preset(Text.translatable("silent_knock.knock.preset.bluemap"),
                "tcp/" + ExtraKnock.BLUEMAP_DEFAULT_PORT));
        return presets;
    }

    @Override
    protected void init() {
        this.contentWidth = Math.min(320, Math.max(200, this.width - 40));
        this.left = (this.width - this.contentWidth) / 2;

        int detectedY = 58;
        boolean hasDetected = !ExtraKnock.detectedMods(this.silentspa$gamePort()).isEmpty();
        this.fieldY = (hasDetected ? detectedY + 12 : detectedY + 4) + 12;

        this.portField = new TextFieldWidget(this.textRenderer, this.left, this.fieldY,
                this.contentWidth, 20, Text.translatable("silent_knock.knock.ports"));
        this.portField.setMaxLength(128);
        this.portField.setText(this.portText == null ? "" : this.portText);
        this.portField.setPlaceholder(Text.translatable("silent_knock.knock.placeholder"));
        this.portField.setTooltip(Tooltip.of(Text.translatable("silent_knock.knock.ports.tooltip")));
        this.portField.setChangedListener(text -> this.portText = text);
        this.addDrawableChild(this.portField);

        // 示例按钮（两列），点一下把端口填进输入框
        List<Preset> presets = this.silentspa$presets();
        int gap = 4;
        int presetWidth = (this.contentWidth - gap) / 2;
        int presetY = this.fieldY + 30;
        for (int i = 0; i < presets.size(); i++) {
            Preset preset = presets.get(i);
            int column = i % 2;
            int row = i / 2;
            int x = this.left + column * (presetWidth + gap);
            int y = presetY + row * 22;
            this.addDrawableChild(ButtonWidget.builder(preset.label(),
                            button -> this.silentspa$fill(preset))
                    .dimensions(x, y, presetWidth, 20)
                    .tooltip(Tooltip.of(Text.translatable("silent_knock.knock.preset.tooltip", preset.entry())))
                    .build());
        }
        int presetRows = (presets.size() + 1) / 2;
        this.statusY = presetY + presetRows * 22 + 2;

        int buttonsY = this.height - 26;
        this.viewTop = this.fieldY - 13;
        // 窗口特别矮时也留一像素可视区，裁剪框不能翻过来
        this.viewBottom = Math.max(this.viewTop + 1, buttonsY - 4);
        this.scrollBarX = this.left + this.contentWidth + 5;

        // 先把会滚的控件记下来（底部按钮还没加，不会被卷进去）
        this.scrollItems.clear();
        for (Element element : this.children()) {
            if (element instanceof ClickableWidget widget) {
                this.scrollItems.add(new ScrollItem(widget, widget.getY()));
            }
        }

        this.fixedButtons.clear();
        int buttonWidth = (this.contentWidth - gap) / 2;
        this.silentspa$fixedButton(ButtonWidget.builder(Text.translatable("silent_knock.knock.send"),
                        button -> this.silentspa$send())
                .dimensions(this.left, buttonsY, buttonWidth, 20)
                .tooltip(Tooltip.of(Text.translatable("silent_knock.knock.send.tooltip")))
                .build());
        this.silentspa$fixedButton(ButtonWidget.builder(Text.translatable("silent_knock.knock.cancel"),
                        button -> this.close())
                .dimensions(this.left + buttonWidth + gap, buttonsY, buttonWidth, 20)
                .build());

        this.silentspa$updateScrollBounds();
        this.silentspa$applyScroll();
    }

    /** 底部按钮：能点，但画的时候单独画在裁剪区外。 */
    private void silentspa$fixedButton(ButtonWidget button) {
        this.addDrawableChild(button);
        this.fixedButtons.add(button);
    }

    /** 点示例按钮：把端口填进去，方便玩家再改。 */
    private void silentspa$fill(Preset preset) {
        this.portText = preset.entry();
        this.portField.setText(this.portText);
        this.portField.setCursorToEnd(false);
        this.status = Text.translatable("silent_knock.knock.preset.filled", preset.entry());
        this.statusError = false;
    }

    private void silentspa$send() {
        String raw = this.portField.getText();
        List<String> entries = FwknopRc.splitAccess(raw);
        if (entries.isEmpty()) {
            this.status = Text.translatable("silent_knock.knock.empty");
            this.statusError = true;
            return;
        }
        // 结果走聊天栏提示，这里直接回游戏，语音模组自己会重连
        ExtraKnock.knock(this.info, entries);
        this.close();
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    /** 内容加起来有多高（状态文字会变，所以每次都重算）。 */
    private int silentspa$contentHeight() {
        int height = this.statusY - this.viewTop;
        if (this.status != null) {
            height += this.textRenderer.getWrappedLinesHeight(this.status, this.contentWidth) + 4;
        }
        height += this.textRenderer.getWrappedLinesHeight(
                Text.translatable("silent_knock.knock.hint"), this.contentWidth) + 2;
        return height;
    }

    private void silentspa$updateScrollBounds() {
        int available = this.viewBottom - this.viewTop;
        this.maxScroll = Math.max(0, this.silentspa$contentHeight() - available);
        this.scroll = Math.min(Math.max(this.scroll, 0), this.maxScroll);
    }

    /** 把滚动量摊到控件上；滑出可视区的控件连点击一起关掉。 */
    private void silentspa$applyScroll() {
        for (ScrollItem item : this.scrollItems) {
            ClickableWidget widget = item.widget();
            int y = item.baseY() - this.scroll;
            widget.setY(y);
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

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.silentspa$updateScrollBounds();
        this.silentspa$applyScroll();

        int centerX = this.width / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, 12, TITLE_COLOR);

        String server = this.silentspa$serverLine();
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(server), centerX, 30, LABEL_COLOR);

        String keyFile = this.silentspa$keyFileLine();
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(keyFile), centerX, 44, LABEL_COLOR);

        String detected = this.silentspa$detectedLine();
        if (!detected.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal(this.textRenderer.trimToWidth(detected, this.width - 20)), centerX, 58, OK_COLOR);
        }
        if (this.maxScroll > 0) {
            Text tip = Text.translatable("silent_knock.create.scroll_hint");
            context.drawTextWithShadow(this.textRenderer, tip,
                    this.left + this.contentWidth - this.textRenderer.getWidth(tip), 12, HINT_COLOR);
        }

        int clipBottom = Math.max(this.viewTop + 1, this.viewBottom);
        context.enableScissor(this.left - 4, this.viewTop, this.left + this.contentWidth + 4, clipBottom);

        // 只画会滚的那几行；底部按钮不在这儿画，免得被裁剪框切掉
        for (ScrollItem item : this.scrollItems) {
            item.widget().render(context, mouseX, mouseY, delta);
        }

        context.drawTextWithShadow(this.textRenderer, Text.translatable("silent_knock.knock.ports"),
                this.left, this.fieldY - 12 - this.scroll, LABEL_COLOR);

        int y = this.statusY - this.scroll;
        if (this.status != null) {
            context.drawWrappedTextWithShadow(this.textRenderer, this.status, this.left, y,
                    this.contentWidth, this.statusError ? ERROR_COLOR : OK_COLOR);
            y += this.textRenderer.getWrappedLinesHeight(this.status, this.contentWidth) + 4;
        }
        context.drawWrappedTextWithShadow(this.textRenderer,
                Text.translatable("silent_knock.knock.hint"),
                this.left, y, this.contentWidth, HINT_COLOR);

        context.disableScissor();
        // 底部按钮钉在屏幕下沿：裁剪关了再画，滚到哪儿它们都在原地
        for (ClickableWidget button : this.fixedButtons) {
            button.render(context, mouseX, mouseY, delta);
        }
        this.silentspa$renderScrollBar(context);
    }

    private void silentspa$renderScrollBar(DrawContext context) {
        if (this.maxScroll <= 0) {
            return;
        }
        int barHeight = this.viewBottom - this.viewTop;
        if (barHeight < 12) {
            return;
        }
        int x = this.scrollBarX;
        context.fill(x, this.viewTop, x + 3, this.viewTop + barHeight, 0x40FFFFFF);
        int thumbHeight = Math.max(14, barHeight * barHeight / (barHeight + this.maxScroll));
        int thumbY = this.viewTop + (barHeight - thumbHeight) * this.scroll / this.maxScroll;
        context.fill(x, thumbY, x + 3, thumbY + thumbHeight, 0xCCFFFFFF);
    }

    private String silentspa$serverLine() {
        if (this.info == null) {
            return Text.translatable("silent_knock.knock.no_server").getString();
        }
        String address = this.info.address == null ? "" : this.info.address.strip();
        String name = this.info.name == null ? "" : this.info.name.strip();
        String text = name.isEmpty() || name.equals(address) ? address : name + "  ·  " + address;
        return this.textRenderer.trimToWidth(text, this.width - 20);
    }

    /** 本机检测到的、会自动带端口的模组；没有就返回空串。 */
    private String silentspa$detectedLine() {
        List<ExtraKnock.Detected> mods = ExtraKnock.detectedMods(this.silentspa$gamePort());
        if (mods.isEmpty()) {
            return "";
        }
        StringBuilder names = new StringBuilder();
        for (ExtraKnock.Detected mod : mods) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(mod.modName());
        }
        return Text.translatable("silent_knock.knock.detected", names.toString()).getString();
    }

    private String silentspa$keyFileLine() {
        if (this.info == null) {
            return "";
        }
        String path = ((SpaHooks) (Object) this.info).silentspa$getKeyPath();
        if (path == null || path.isBlank()) {
            return Text.translatable("silent_knock.button.unbound").getString();
        }
        String fileName;
        try {
            Path p = Path.of(path);
            fileName = p.getFileName() == null ? path : p.getFileName().toString();
        } catch (RuntimeException e) {
            fileName = path;
        }
        return Text.translatable("silent_knock.knock.key", fileName).getString();
    }
}
