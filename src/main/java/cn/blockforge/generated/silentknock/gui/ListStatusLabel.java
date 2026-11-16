package cn.blockforge.generated.silentknock.gui;

import cn.blockforge.generated.silentknock.config.SpaConfig;
import cn.blockforge.generated.silentknock.logic.ListPhase;
import cn.blockforge.generated.silentknock.logic.SpaManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

/**
 * 服务器列表里，紧跟在服务器名字右边的那一小行状态字：
 *
 * <ul>
 *   <li><b>Knocking</b>（金色）：这台服务器绑了 SPA 密钥，正在后台敲门；</li>
 *   <li><b>Pinging</b>（灰色）：门敲完了、或者这台服务器本来就不用敲门，正在等服务器回 MOTD。</li>
 * </ul>
 *
 * <p>以前这两步是靠右上角的提示框告诉玩家的，进一次列表弹一次，太吵。现在直接写在列表上，
 * 服务器回过状态（连上 / 连不上）之后这行字就自己消失，换回原版的 MOTD 和延迟图标。
 *
 * <p>坐标和原版对齐：原版把服务器名字画在 {@code (x + 32 + 3, y + 1)}
 * （32 是图标宽度、3 是间距、1 是上边距），这里就接着名字的右端往后排。
 */
public final class ListStatusLabel {
    /** 原版服务器名字的起始横向偏移（图标宽 32 + 间距 3）。 */
    private static final int TEXT_X = 32 + 3;
    /** 原版服务器名字的上边距。 */
    private static final int TEXT_Y = 1;
    /** 状态字和服务器名字之间留几个像素。 */
    private static final int GAP = 4;
    /** 右边给原版的延迟图标（10×8）留的宽度，别让字压上去。 */
    private static final int RIGHT_MARGIN = 18;
    /** 敲门中的颜色：金色，和左上角的小盾牌呼应。 */
    private static final int KNOCKING_COLOR = 0xFFFFAA00;
    /** Ping 中的颜色：浅灰，比原版 MOTD 的深灰亮一点，在黑底上看清。 */
    private static final int PINGING_COLOR = 0xFFAAAAAA;

    private static final Text KNOCKING = Text.translatable("silent_knock.list.knocking");
    private static final Text PINGING = Text.translatable("silent_knock.list.pinging");

    private ListStatusLabel() {
    }

    /**
     * 在这一行服务器名字右边画状态字。
     *
     * @param x         这一行的左边缘（原版把 32×32 图标画在这里）
     * @param y         这一行的上边缘
     * @param entryWidth 这一行的宽度，用来看右边还剩多少地方
     */
    public static void draw(DrawContext context, ServerInfo server, int x, int y, int entryWidth) {
        if (server == null || !SpaConfig.get().enabled) {
            return;
        }
        ServerInfo.Status status = server.getStatus();
        if (status != ServerInfo.Status.INITIAL && status != ServerInfo.Status.PINGING) {
            // 已经出结果了（连上 / 连不上 / 版本不对），这行字就该让位给原版的 MOTD
            ListPhase.clear(server);
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        TextRenderer font = client.textRenderer;
        String name = server.name == null ? "" : server.name;
        // 绑了密钥、而且现在确实在敲门，才显示 Knocking；没绑密钥的服务器直接就是 Pinging
        boolean knocking = SpaManager.hasKey(server) && ListPhase.phaseOf(server) == ListPhase.Phase.KNOCKING;
        Text text = knocking ? KNOCKING : PINGING;
        int textX = x + TEXT_X + font.getWidth(name) + GAP;
        int textY = y + TEXT_Y;
        if (textX + font.getWidth(text) > x + entryWidth - RIGHT_MARGIN) {
            // 名字太长、右边没地方了，就别挤到延迟图标下面去
            return;
        }
        context.drawTextWithShadow(font, text, textX, textY, knocking ? KNOCKING_COLOR : PINGING_COLOR);
    }
}
