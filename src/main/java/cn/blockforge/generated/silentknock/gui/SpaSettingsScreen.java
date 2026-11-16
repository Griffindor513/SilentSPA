package cn.blockforge.generated.silentknock.gui;

import cn.blockforge.generated.silentknock.config.SpaConfig;
import cn.blockforge.generated.silentknock.config.ToastMode;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/**
 * SilentSPA 自己的设置界面。
 *
 * <p>入口在原版「选项」界面的左下角（按钮写的就是 SilentSPA）：不装 ModMenu 也能点进来。
 * 能改的东西：
 * <ul>
 *   <li><b>提示方式</b>：静默 / 全部提示 / 游戏中不提示 / 游戏中只走聊天栏；</li>
 *   <li><b>聊天栏提示</b>、<b>提示音</b>两个开关；</li>
 *   <li><b>敲门后等待</b>：就是配置文件里的 {@code delayMs}，拖一下即可，不用再手改 JSON。</li>
 * </ul>
 *
 * <p>改一项就立刻存进 {@code config/silent_knock.json}，下次启动照旧读回来。
 */
public final class SpaSettingsScreen extends Screen {
    private static final int LABEL_COLOR = 0xFFA0A0A0;
    private static final int HINT_COLOR = 0xFF909090;
    private static final int WIDGET_WIDTH = 150;
    private static final int ROW_HEIGHT = 26;
    private static final int WIDGET_HEIGHT = 20;

    private final Screen parent;

    private int left;
    private int contentWidth;
    private int widgetX;
    private int firstRowY;
    private int hintY;

    public SpaSettingsScreen(Screen parent) {
        super(Text.translatable("silent_knock.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        SpaConfig cfg = SpaConfig.get();
        this.contentWidth = Math.min(360, Math.max(220, this.width - 40));
        this.left = (this.width - this.contentWidth) / 2;
        this.widgetX = this.left + this.contentWidth - WIDGET_WIDTH;
        this.firstRowY = 52;

        // 1) 提示方式：静默 / 全部提示 / 游戏中不提示 / 游戏中只走聊天栏
        this.addDrawableChild(CyclingButtonWidget
                .<ToastMode>builder(mode -> Text.translatable(mode.translationKey()))
                .values(ToastMode.values())
                .initially(ToastMode.orDefault(cfg.toastMode))
                .tooltip(value -> Tooltip.of(Text.translatable(
                        value instanceof ToastMode mode
                                ? mode.descriptionKey()
                                : "silent_knock.settings.toast_mode.all.desc")))
                .build(this.widgetX, this.firstRowY, WIDGET_WIDTH, WIDGET_HEIGHT,
                        Text.translatable("silent_knock.settings.toast_mode"),
                        (button, mode) -> {
                            cfg.toastMode = mode;
                            cfg.save();
                        }));

        // 2) 聊天栏提示
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(cfg.chatFeedback)
                .build(this.widgetX, this.firstRowY + ROW_HEIGHT, WIDGET_WIDTH, WIDGET_HEIGHT,
                        Text.translatable("silent_knock.settings.chat"),
                        (button, value) -> {
                            cfg.chatFeedback = value;
                            cfg.save();
                        }));

        // 3) 提示音
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(cfg.soundFeedback)
                .build(this.widgetX, this.firstRowY + ROW_HEIGHT * 2, WIDGET_WIDTH, WIDGET_HEIGHT,
                        Text.translatable("silent_knock.settings.sound"),
                        (button, value) -> {
                            cfg.soundFeedback = value;
                            cfg.save();
                        }));

        // 4) 敲门后等多久再放行连接（配置文件里的 delayMs）
        this.addDrawableChild(new DelaySlider(this.widgetX, this.firstRowY + ROW_HEIGHT * 3,
                WIDGET_WIDTH, WIDGET_HEIGHT, cfg));

        this.hintY = this.firstRowY + ROW_HEIGHT * 4 + 4;

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("silent_knock.settings.done"),
                        button -> this.close())
                .dimensions(this.width / 2 - 75, this.height - 27, 150, WIDGET_HEIGHT)
                .build());
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int centerX = this.width / 2;

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, 16, 0xFFFFFFFF);

        this.silentspa$drawLabel(context, Text.translatable("silent_knock.settings.toast_mode"), 0);
        this.silentspa$drawLabel(context, Text.translatable("silent_knock.settings.chat"), 1);
        this.silentspa$drawLabel(context, Text.translatable("silent_knock.settings.sound"), 2);
        this.silentspa$drawLabel(context, Text.translatable("silent_knock.settings.delay_label"), 3);

        int bottom = this.height - 34;
        if (this.hintY < bottom) {
            context.enableScissor(this.left, this.hintY, this.left + this.contentWidth, bottom);
            context.drawWrappedTextWithShadow(this.textRenderer,
                    Text.translatable("silent_knock.settings.hint"),
                    this.left, this.hintY, this.contentWidth, HINT_COLOR);
            context.disableScissor();
        }
    }

    private void silentspa$drawLabel(DrawContext context, Text label, int row) {
        int y = this.firstRowY + ROW_HEIGHT * row + (WIDGET_HEIGHT - 8) / 2;
        context.drawTextWithShadow(this.textRenderer, label, this.left, y, LABEL_COLOR);
    }

    /**
     * 「敲门后等待」的滑条：0~3000 毫秒，每格 100 毫秒。
     *
     * <p>为什么要等：fwknopd 收到敲门包后要在防火墙里把口开出来，敲完立刻连可能还被挡着。
     */
    private static final class DelaySlider extends SliderWidget {
        private static final int MAX_MS = 3000;
        private static final int STEP_MS = 100;

        private final SpaConfig config;

        DelaySlider(int x, int y, int width, int height, SpaConfig config) {
            super(x, y, width, height, Text.empty(), toSlider(config.delayMs));
            this.config = config;
            this.updateMessage();
        }

        private static double toSlider(int delayMs) {
            int clamped = Math.max(0, Math.min(MAX_MS, delayMs));
            return (double) clamped / MAX_MS;
        }

        private static int toMillis(double sliderValue) {
            int ms = (int) Math.round(sliderValue * MAX_MS / STEP_MS) * STEP_MS;
            return Math.max(0, Math.min(MAX_MS, ms));
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Text.translatable("silent_knock.settings.delay",
                    Integer.toString(toMillis(this.value))));
        }

        @Override
        protected void applyValue() {
            this.config.delayMs = toMillis(this.value);
            this.config.save();
        }
    }
}
