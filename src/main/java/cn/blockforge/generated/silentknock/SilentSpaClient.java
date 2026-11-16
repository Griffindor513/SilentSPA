package cn.blockforge.generated.silentknock;

import cn.blockforge.generated.silentknock.config.SpaConfig;
import cn.blockforge.generated.silentknock.logic.ClockCheck;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SilentSPA 客户端入口。
 *
 * <p>这个模组只做一件事：在连接 / 刷新一个已经绑定 SPA 密钥文件的服务器之前，
 * 先异步发一个标准 fwknop UDP 敲门包，再按配置的延迟放行原版连接。
 */
public final class SilentSpaClient implements ClientModInitializer {
    public static final String MOD_ID = "silent_knock";
    public static final Logger LOGGER = LoggerFactory.getLogger("SilentSPA");

    @Override
    public void onInitializeClient() {
        SpaConfig cfg = SpaConfig.get();
        LOGGER.info("[SPA] 客户端已初始化：端口 {}，延迟 {} ms，敲门后放行原生连接", cfg.spaPort, cfg.delayMs);
        // 先在后台对一次网络时间，等玩家进服务器列表时结果一般已经出来了
        ClockCheck.refreshAsync(false);
    }
}
