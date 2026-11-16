package cn.blockforge.generated.silentknock;

/**
 * 通过 Mixin 挂到原版 {@link net.minecraft.client.network.ServerInfo} 上的附加字段。
 *
 * <p>密钥只保存服务器端密钥文件的<b>绝对路径</b>，内容永远不写进 servers.dat。
 */
public interface SpaHooks {
    /** @return 密钥文件绝对路径，未绑定返回 null */
    String silentspa$getKeyPath();

    /** 传 null 或空串表示解除绑定。 */
    void silentspa$setKeyPath(String path);

    /** @return 该服务器单独的 SPA UDP 端口，-1 表示用全局配置 */
    int silentspa$getSpaPort();

    /** 传 &lt;=0 或 &gt;65535 表示恢复为全局配置。 */
    void silentspa$setSpaPort(int port);
}
