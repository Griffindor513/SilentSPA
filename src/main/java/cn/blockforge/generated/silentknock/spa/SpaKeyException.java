package cn.blockforge.generated.silentknock.spa;

import net.minecraft.text.Text;

/**
 * 密钥文件读取 / 格式错误。
 *
 * <p>消息存的是翻译键（不是写死的中文），展示时由游戏按当前语言渲染，
 * 这样切换语言设置就能跟着变。
 */
public class SpaKeyException extends Exception {
    private final Text text;

    public SpaKeyException(String translationKey, Object... args) {
        super(translationKey + (args.length == 0 ? "" : " " + java.util.Arrays.toString(args)));
        Object[] converted = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            converted[i] = args[i] instanceof Text ? args[i] : String.valueOf(args[i]);
        }
        this.text = Text.translatable(translationKey, converted);
    }

    /** 按当前游戏语言渲染好的提示文本。 */
    public Text getText() {
        return text;
    }

    @Override
    public String getMessage() {
        return text.getString();
    }
}
