package cn.blockforge.generated.silentknock.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;

import java.nio.file.Path;

/**
 * “SPA 密钥”那一行控件的公共尺寸与文案。
 *
 * <p>“添加 / 编辑服务器”和“直接连接”两个界面都要摆同一行控件：
 * 密钥按钮 + 端口输入框，绑定了密钥以后中间再多一个“解绑”小按钮。
 * 两边的宽度算法、按钮文字截断、提示文字都在这里算，免得改了一边忘了另一边。
 *
 * <p>绑定了密钥时密钥按钮要让出位置给小按钮，所以宽度会变窄：
 * <ul>
 *   <li>未绑定：密钥按钮 + 端口框（原来的样子）；</li>
 *   <li>已绑定：密钥按钮 + 解绑按钮 + 端口框。</li>
 * </ul>
 */
public final class SpaKeyRow {
    /** 端口输入框宽度，够放 5 位数。 */
    public static final int PORT_WIDTH = 65;
    /** “解绑”小按钮的最小宽度：两个汉字加一点内边距。 */
    public static final int UNBIND_WIDTH = 28;
    /** 密钥按钮和端口框之间原本的间距。 */
    public static final int GAP = 3;
    /** “解绑”小按钮的最大宽度，再宽就挤到端口框了。 */
    private static final int UNBIND_MAX_WIDTH = 80;
    /** 密钥按钮最小宽度，再窄就点不动了。 */
    private static final int KEY_MIN = 64;

    private SpaKeyRow() {
    }

    /** 这个路径算不算“已经绑定了密钥”。 */
    public static boolean isBound(String path) {
        return path != null && !path.isBlank();
    }

    /** 路径最后一段（文件名）；拿不到就返回原路径。 */
    public static String fileName(String path) {
        if (!isBound(path)) {
            return "";
        }
        try {
            Path file = Path.of(path);
            Path name = file.getFileName();
            return name == null ? path : name.toString();
        } catch (RuntimeException e) {
            return path;
        }
    }

    /**
     * 密钥按钮上的文字。
     *
     * <p>没绑定显示“SPA 密钥：未绑定”；绑定了只显示文件名，太长了截断加省略号
     * （完整路径在按钮的提示气泡里）。
     */
    public static Text label(TextRenderer textRenderer, String path, int width) {
        if (!isBound(path)) {
            return Text.translatable("silent_knock.button.unbound");
        }
        String name = fileName(path);
        int max = Math.max(16, width - 10);
        if (textRenderer.getWidth(name) > max) {
            name = textRenderer.trimToWidth(name, Math.max(8, max - 8)) + "…";
        }
        return Text.literal(name);
    }

    /** 密钥按钮的提示气泡：没绑定时提示去选文件，绑定了显示完整路径。 */
    public static Tooltip tooltip(String path) {
        if (!isBound(path)) {
            return Tooltip.of(Text.translatable("silent_knock.button.tooltip"));
        }
        return Tooltip.of(Text.translatable("silent_knock.button.tooltip.bound", path));
    }

    /** 密钥按钮的宽度。{@code unbindWidth} 传 0 表示这次不显示解绑按钮。 */
    public static int keyWidth(int total, int unbindWidth) {
        int gap = unbindWidth > 0 ? GAP * 2 : 5;
        return Math.max(KEY_MIN, total - PORT_WIDTH - unbindWidth - gap);
    }

    /** “解绑”按钮的宽度：按当前语言的按钮文字量出来，免得长单词溢出按钮。 */
    public static int unbindWidth(TextRenderer textRenderer, Text label) {
        int width = textRenderer.getWidth(label) + 14;
        return Math.max(UNBIND_WIDTH, Math.min(width, UNBIND_MAX_WIDTH));
    }

    /** “解绑”按钮的 x 坐标。 */
    public static int unbindX(int x, int total, int unbindWidth) {
        return x + keyWidth(total, unbindWidth) + GAP;
    }

    /** 端口输入框的 x 坐标（始终贴右端，解绑按钮出现时也不会被挤走）。 */
    public static int portX(int x, int total) {
        return x + total - PORT_WIDTH;
    }
}
