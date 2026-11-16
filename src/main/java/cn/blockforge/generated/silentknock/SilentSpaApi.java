package cn.blockforge.generated.silentknock;

import cn.blockforge.generated.silentknock.logic.ExtraKnock;
import cn.blockforge.generated.silentknock.logic.SpaManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

/**
 * 给别的模组用的小接口：需要临时打开服务器上的另一个端口时，调一下就行。
 *
 * <p>典型场景是语音模组：它自己打开 UDP 端口去连服务器，防火墙没放行就静默失败。
 * 这个模组没法替它拦一道，但可以在需要的时候替它敲一次门，让服务器先放行：
 *
 * <pre>{@code
 * SilentSpaApi.requestPort("udp", 24454);
 * }</pre>
 *
 * <p>调用方不需要关心线程：内部会切回主线程，再用当前服务器绑定的 SPA 密钥文件敲门。
 * 门会开一小段时间（多久由服务器 fwknopd 的 FW_ACCESS_TIMEOUT 决定，默认 30 秒），
 * 之后需要再敲一次。若当前服务器没绑定密钥文件、或者正在单机，调用会被安静地忽略。
 *
 * <p>本类没有任何硬依赖：没装本模组时调用方只要用
 * {@code FabricLoader.isModLoaded("silent_knock")} 判一下即可。
 */
public final class SilentSpaApi {
    private SilentSpaApi() {
    }

    /**
     * 为当前服务器临时敲一个端口。
     *
     * @param protocol {@code tcp} / {@code udp}
     * @param port     1~65535
     * @return true 表示请求已受理（不代表服务器一定放行）
     */
    public static boolean requestPort(String protocol, int port) {
        return requestPorts(protocol + "/" + port);
    }

    /**
     * 为当前服务器临时敲一组端口。
     *
     * @param access 逗号隔开的端口列表，例如 {@code "udp/24454,udp/25565"}
     * @return true 表示请求已受理
     */
    public static boolean requestPorts(String access) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return false;
        }
        ServerInfo info = client.getCurrentServerEntry();
        if (info == null) {
            return false;
        }
        client.execute(() -> ExtraKnock.knock(client.getCurrentServerEntry(), java.util.List.of(access)));
        return true;
    }

    /** 当前服务器是否绑定了 SPA 密钥文件（绑了才值得调上面的方法）。 */
    public static boolean isCurrentServerBound() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return false;
        }
        ServerInfo info = client.getCurrentServerEntry();
        return info != null && SpaManager.hasKey(info);
    }
}
