package cn.blockforge.generated.silentknock.gui;

import cn.blockforge.generated.silentknock.SilentSpaClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * 服务器列表里那个“这台服务器要用 SPA 敲门”的金色小盾牌。
 *
 * <p>画在每一行左上角、刚好压住服务器图标的一个角，金色代表“这门要先敲一下才开”。
 * 尺寸只有 10×10（原版图标是 32×32），所以只挡住一个角，不遮住图标本身。
 * 只画图，不做逻辑，判断有没有绑定密钥是调用方（{@code ServerEntryMixin}）的事。
 */
public final class SpaBadge {
    /** 原始素材是 16×16，按这个尺寸缩放着画，素材本身不用改。 */
    private static final Identifier SHIELD =
            Identifier.of(SilentSpaClient.MOD_ID, "textures/gui/spa_shield.png");
    /** 素材边长（像素）。 */
    public static final int TEXTURE_SIZE = 16;
    /** 画到屏幕上多大：图标是 32×32，10 刚好只压住左上角一个角。 */
    public static final int SIZE = 10;

    private SpaBadge() {
    }

    /**
     * 在指定位置画一个小盾牌。
     *
     * @param x 左上角横坐标（屏幕像素），一般直接传这一行的 {@code x}
     * @param y 左上角纵坐标（屏幕像素），一般直接传这一行的 {@code y}
     */
    public static void draw(DrawContext context, int x, int y) {
        // 12 个参数的版本才能“把整张 16×16 缩成 10×10”；
        // 10 个参数的那个是把贴图左上角裁一块，盾牌会被切掉一半。
        context.drawTexture(RenderPipelines.GUI_TEXTURED, SHIELD, x, y, 0.0F, 0.0F,
                SIZE, SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
    }
}
