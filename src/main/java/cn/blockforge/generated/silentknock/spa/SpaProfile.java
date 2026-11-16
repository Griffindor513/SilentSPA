package cn.blockforge.generated.silentknock.spa;

/**
 * 一个 fwknoprc 配置段（stanza）：一整套“怎么敲门”的参数。
 *
 * <p>字段名和 fwknop 客户端 {@code .fwknoprc} 里的指令一一对应，
 * 这样模组生成的文件既能自己读，也能直接丢给 {@code fwknop -n 段名} 用。
 *
 * @param section   段名（{@code [name]}），只用于写文件
 * @param server    {@code SPA_SERVER}：fwknopd 的地址，可以为空（空表示用服务器列表里的地址）
 * @param serverPort {@code SPA_SERVER_PORT}：敲门包发往的 UDP 端口，0 表示用全局配置
 * @param proto     {@code SPA_SERVER_PROTO}：目前只支持 udp
 * @param keys      两把密钥
 * @param digestType {@code DIGEST_TYPE}：报文摘要算法
 * @param hmacType  {@code HMAC_DIGEST_TYPE}：HMAC 算法
 * @param allowIp   {@code ALLOW_IP}：{@code source} 或具体 IP
 * @param msgType   消息类型，1 = 打开端口、2 = 关闭端口
 * @param access    {@code ACCESS}：要打开的端口列表，例如 {@code tcp/25565,udp/24454}
 */
public record SpaProfile(
        String section,
        String server,
        int serverPort,
        String proto,
        SpaKeyMaterial keys,
        String digestType,
        String hmacType,
        String allowIp,
        int msgType,
        String access) {

    /** fwknop 的默认摘要算法（fwknop 2.5 之后就是 SHA256）。 */
    public static final String DEFAULT_DIGEST_TYPE = "sha256";
    /** 默认 HMAC 算法。 */
    public static final String DEFAULT_HMAC_TYPE = "sha256";
    /** {@code ALLOW_IP source}：让服务器用 UDP 包的源地址，家庭宽带后面最省事。 */
    public static final String ALLOW_IP_SOURCE = "source";
    /** {@code SPA_SERVER_PORT} 的默认值。 */
    public static final int DEFAULT_SPA_PORT = 62201;
    /** 打开端口。 */
    public static final int MSG_TYPE_OPEN_PORT = 1;
    /** 关闭端口。 */
    public static final int MSG_TYPE_CLOSE_PORT = 2;

    public SpaProfile {
        if (proto == null || proto.isBlank()) {
            proto = "udp";
        }
        if (digestType == null || digestType.isBlank()) {
            digestType = DEFAULT_DIGEST_TYPE;
        }
        if (hmacType == null || hmacType.isBlank()) {
            hmacType = DEFAULT_HMAC_TYPE;
        }
        if (allowIp == null) {
            allowIp = "";
        }
        if (msgType < 1 || msgType > 5) {
            msgType = MSG_TYPE_OPEN_PORT;
        }
        if (access == null) {
            access = "";
        }
        if (server == null) {
            server = "";
        }
        if (section == null || section.isBlank()) {
            section = "default";
        }
    }

    /**
     * 写进敲门包里的 allow IP。
     *
     * <p>{@code ALLOW_IP source} 在 fwknop 里对应报文中的 {@code 0.0.0.0}，
     * 由 fwknopd 换成 UDP 包的源地址。配置文件里没写 {@code ALLOW_IP} 时用
     * {@code fallback}（来自模组全局配置）。
     */
    public String wireAllowIp(String fallback) {
        String value = allowIp == null || allowIp.isBlank()
                ? (fallback == null ? "" : fallback.strip())
                : allowIp.strip();
        if (value.isEmpty() || value.equalsIgnoreCase(ALLOW_IP_SOURCE) || value.equalsIgnoreCase("resolve")) {
            return "0.0.0.0";
        }
        return value;
    }
}
