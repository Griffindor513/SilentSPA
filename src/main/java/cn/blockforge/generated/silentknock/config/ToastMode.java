package cn.blockforge.generated.silentknock.config;

/**
 * 敲门结果的提示方式，对应设置界面里那一个可以点着切换的按钮。
 *
 * <p>四个档位的含义：
 * <ul>
 *   <li>{@link #SILENT} 静默：不弹右上角提示、不刷聊天栏、也不响；</li>
 *   <li>{@link #ALL} 全部提示：任何界面都弹右上角提示，在游戏里同时刷聊天栏；</li>
 *   <li>{@link #INGAME_OFF} 游戏中不提示：进了世界就彻底安静，只在标题界面 / 连接界面弹提示；</li>
 *   <li>{@link #INGAME_CHAT_ONLY} 游戏中只走聊天栏：玩游戏时只刷聊天栏不弹框，菜单里才弹提示。</li>
 * </ul>
 *
 * <p>名字就是写进 {@code config/silent_knock.json} 里 {@code toastMode} 的值。
 */
public enum ToastMode {
    SILENT("silent"),
    ALL("all"),
    INGAME_OFF("ingame_off"),
    INGAME_CHAT_ONLY("ingame_chat");

    private final String id;

    ToastMode(String id) {
        this.id = id;
    }

    /** 翻译键里用的短名字。 */
    public String id() {
        return this.id;
    }

    /** 设置界面里显示的名字，例如 {@code silent_knock.settings.toast_mode.silent}。 */
    public String translationKey() {
        return "silent_knock.settings.toast_mode." + this.id;
    }

    /** 切换按钮的悬停说明，讲清这一档到底提示什么。 */
    public String descriptionKey() {
        return this.translationKey() + ".desc";
    }

    /** 配置文件里读出来是空的（老配置 / 手写错）时用哪个。 */
    public static ToastMode orDefault(ToastMode mode) {
        return mode == null ? ALL : mode;
    }
}
