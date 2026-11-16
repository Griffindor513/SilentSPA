package cn.blockforge.generated.silentknock.compat;

import cn.blockforge.generated.silentknock.gui.SpaSettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * 和 Mod Menu（模组列表模组）的对接：在模组列表里点 SilentSPA 的「设置」按钮，
 * 打开的就是原版「选项 → SilentSPA」那个界面 —— 同一个界面，同一个配置文件，
 * 不存在两套设置。
 *
 * <p><b>没装 Mod Menu 会怎样：</b>这个类根本不会被加载。它在 {@code fabric.mod.json}
 * 里挂在 {@code modmenu} 这个入口点下，而这个入口点只有 Mod Menu 自己去取；
 * 所以「可选依赖」不需要任何 try/catch，也不会拖累不装 Mod Menu 的玩家。
 *
 * <p>入口还在原版「选项」界面的底栏（{@code OptionsScreenMixin}），两种装法都能进。
 */
public final class SilentSpaModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Mod Menu 点「设置」时会把「模组列表」界面传进来，退出设置就回到那里
        return SpaSettingsScreen::new;
    }
}
