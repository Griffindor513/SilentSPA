package cn.blockforge.generated.silentknock.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * 长得像“成就达成”那种框的提示弹窗，用来在敲门失败时告诉玩家原因。
 *
 * <p>直接借用了原版成就提示的底图（{@code minecraft:toast/advancement}），
 * 所以外框、圆角、左边那个物品格子都和成就框一模一样；区别只是：
 * <ul>
 *   <li>左边的图标是栅栏（表示“没打通”）；</li>
 *   <li>标题是红色的“敲门失败”，下面一行写具体原因；</li>
 *   <li>默认 4 秒后自己消失（和成就提示一样，会跟随“通知显示时长”设置缩放）。</li>
 * </ul>
 *
 * <p>只负责画面，不碰游戏逻辑，任何界面（包括主菜单、连接界面）上都能弹出来。
 * 失败时用红色的“敲门失败”+ 栅栏图标；自动敲门成功时用绿色的提示 + 一扇门图标
 * （见 {@link #autoKnock}）。
 */
public final class SpaToast implements Toast {
    /** 原版成就提示的底图。 */
    private static final Identifier TEXTURE = Identifier.ofVanilla("toast/advancement");
    private static final int TEXT_X = 30;
    private static final int TEXT_WIDTH = 125;
    private static final int FAIL_TITLE_COLOR = 0xFFFF6060;
    private static final int LINE_COLOR = 0xFFFFFFFF;

    private final Text title;
    private final Text line;
    private final long durationMs;
    private final boolean withSound;
    private final ItemStack icon;
    private final int titleColor;

    private Visibility visibility = Visibility.HIDE;

    public SpaToast(Text title, Text line, long durationMs, boolean withSound) {
        this(title, line, durationMs, withSound, new ItemStack(Items.BARRIER), FAIL_TITLE_COLOR);
    }

    public SpaToast(Text title, Text line, long durationMs, boolean withSound,
                    ItemStack icon, int titleColor) {
        this.title = title;
        this.line = line;
        this.durationMs = Math.max(500L, durationMs);
        this.withSound = withSound;
        this.icon = icon == null ? ItemStack.EMPTY : icon.copy();
        this.titleColor = titleColor;
    }

    /** “检测到 XXX 模组，已自动敲门”的提示：绿色的字 + 一扇开着的门图标。 */
    public static SpaToast autoKnock(Text title, Text line, long durationMs, boolean withSound) {
        return new SpaToast(title, line, durationMs, withSound,
                new ItemStack(Items.OAK_DOOR), 0xFF55FF55);
    }

    @Override
    public Visibility getVisibility() {
        return this.visibility;
    }

    @Override
    public void update(ToastManager manager, long time) {
        double lifetime = this.durationMs * manager.getNotificationDisplayTimeMultiplier();
        this.visibility = time >= lifetime ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public SoundEvent getSoundEvent() {
        return this.withSound ? SoundEvents.BLOCK_NOTE_BLOCK_BASS.value() : null;
    }

    @Override
    public void draw(DrawContext context, TextRenderer textRenderer, long startTime) {
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, this.getWidth(), this.getHeight());
        if (!this.icon.isEmpty()) {
            context.drawItemWithoutEntity(this.icon, 8, 8);
        }
        drawFirstLine(context, textRenderer, this.title, 7, this.titleColor);
        drawFirstLine(context, textRenderer, this.line, 18, LINE_COLOR);
    }

    private static void drawFirstLine(DrawContext context, TextRenderer textRenderer,
                                      Text text, int y, int color) {
        if (text == null) {
            return;
        }
        List<OrderedText> lines = textRenderer.wrapLines(text, TEXT_WIDTH);
        if (lines.isEmpty()) {
            return;
        }
        context.drawText(textRenderer, lines.get(0), TEXT_X, y, color, false);
    }
}
