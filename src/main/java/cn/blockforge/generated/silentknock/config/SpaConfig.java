package cn.blockforge.generated.silentknock.config;

import cn.blockforge.generated.silentknock.SilentSpaClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 客户端 JSON 配置，位于 {@code config/silent_knock.json}。
 *
 * <p>读取失败、字段缺失或数值越界都会安静地回退到默认值，绝不影响游戏启动。
 */
public final class SpaConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile SpaConfig instance;

    /** 总开关，关掉后所有服务器都直接走原版连接。 */
    public boolean enabled = true;

    /** fwknopd 的 SPA 监听端口；单个服务器可以在“添加服务器”界面里覆盖。 */
    public int spaPort = 62201;

    /** 敲门包发出后等多少毫秒再放行原生 TCP 连接。 */
    public int delayMs = 1000;

    /** UDP 发送的超时上限（毫秒），超时后回退为直接连接。 */
    public int udpTimeoutMs = 500;

    /** 同一服务器在这个时间内已经敲过门，就不再重复敲门（服务器列表刷新时用）。 */
    public int knockCooldownMs = 4000;

    /**
     * 敲门包里的 allow IP。0.0.0.0 表示让 fwknopd 使用 UDP 包的源地址，
     * 对家庭宽带 / NAT 后面的客户端最省事。
     *
     * <p>SPA 配置文件里写了 {@code ALLOW_IP} 时以文件为准，这里是没写时的默认值。
     */
    public String allowIp = "0.0.0.0";

    /**
     * 兜底的访问项。正常情况下模组会按“这次要连的服务器端口”自动算
     * （例如 tcp/25565），只有连地址都解析不出来时才用这个值。
     */
    public String accessRequest = "tcp/25565";

    /** 敲门包里的用户名；留空则使用当前正版用户名（只保留字母数字和 _ . -）。 */
    public String username = "";

    /** 是否在聊天栏显示敲门状态。 */
    public boolean chatFeedback = true;

    /**
     * 敲门结果怎么提示：静默 / 全部提示 / 游戏中不提示 / 游戏中只走聊天栏。
     *
     * <p>游戏里按 ESC →「选项」→「SilentSPA」就能改，也能直接改这个字段。
     */
    public ToastMode toastMode = ToastMode.ALL;

    /**
     * 旧版的「弹 Toast」开关，只用于从老配置迁移，新配置里不会再写出来。
     *
     * <p>读到旧配置（有 {@code toastFeedback} 没有 {@code toastMode}）时：
     * {@code false} 视为静默，{@code true} 视为全部提示。
     */
    public Boolean toastFeedback;

    /** 是否播放提示音（发送 / 成功 / 失败三态）。 */
    public boolean soundFeedback = true;

    /** “自动敲门”提示框停留多久（毫秒），默认 4 秒。 */
    public int toastDurationMs = 4000;

    /** 文件选择器上次打开的目录。 */
    public String lastKeyDir = "";

    /**
     * 装了会自动识别出额外端口的模组（Simple Voice Chat、Plasmo Voice）时，
     * 连服敲门要不要把它们的端口（{@code udp/24454}、{@code udp/<游戏端口>}）一起请求。
     *
     * <p>打开后玩家不用再往 fwknoprc 里写额外端口：模组随后自己去连时，门已经开好了，
     * 并且会弹一个“XXX mod detected, knocked xxx automatically”的提示。
     */
    public boolean autoVoicePort = true;

    /**
     * 想每次敲门都自动带上的额外端口，逗号隔开，例如 {@code udp/24454,udp/25565}。
     *
     * <p>一般留空即可：语音端口会自动带，临时需要的端口在游戏里按 ESC →「SPA 敲门」补敲。
     */
    public String autoExtraPorts = "";

    /** 同一个额外端口在这个时间内不重复敲门（毫秒），避免连点狂敲。 */
    public int extraPortCooldownMs = 15_000;

    /**
     * 是否校验本机系统时间（进多人游戏列表时自动检查一次）。
     *
     * <p>fwknop 的包正文带时间戳，电脑时间偏差太大（默认超过 120 秒）服务器会直接丢包。
     * 打开后会向公共 NTP 服务器对一下时间，偏差太大就弹提示，连接失败的原因里也会多写一句。
     */
    public boolean timeCheck = true;

    /** 校验用的 NTP 服务器，默认 pool.ntp.org。 */
    public String timeServer = "pool.ntp.org";

    /** 允许的时间偏差（秒），fwknopd 默认也是 120 秒，别随便改大。 */
    public int timeToleranceSeconds = 120;

    public static SpaConfig get() {
        SpaConfig cfg = instance;
        if (cfg == null) {
            synchronized (SpaConfig.class) {
                cfg = instance;
                if (cfg == null) {
                    cfg = load();
                }
            }
        }
        return cfg;
    }

    public static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("silent_knock.json");
    }

    private static SpaConfig load() {
        Path path = configPath();
        SpaConfig cfg = new SpaConfig();
        try {
            if (Files.isRegularFile(path)) {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                SpaConfig parsed = GSON.fromJson(json, SpaConfig.class);
                if (parsed != null) {
                    cfg = parsed;
                }
            }
        } catch (Exception e) {
            SilentSpaClient.LOGGER.warn("[SPA] 配置读取失败，回退到默认值：{}", e.toString());
            cfg = new SpaConfig();
        }
        cfg.sanitize();
        instance = cfg;
        cfg.save();
        return cfg;
    }

    private void sanitize() {
        if (spaPort < 1 || spaPort > 65535) {
            spaPort = 62201;
        }
        delayMs = clamp(delayMs, 0, 10_000);
        udpTimeoutMs = clamp(udpTimeoutMs, 100, 5_000);
        knockCooldownMs = clamp(knockCooldownMs, 0, 60_000);
        if (allowIp == null || allowIp.isBlank()) {
            allowIp = "0.0.0.0";
        }
        if (accessRequest == null || accessRequest.isBlank()) {
            accessRequest = "tcp/25565";
        }
        if (username == null) {
            username = "";
        }
        if (lastKeyDir == null) {
            lastKeyDir = "";
        }
        if (autoExtraPorts == null) {
            autoExtraPorts = "";
        }
        extraPortCooldownMs = clamp(extraPortCooldownMs, 0, 300_000);
        if (timeServer == null || timeServer.isBlank()) {
            timeServer = "pool.ntp.org";
        }
        timeToleranceSeconds = clamp(timeToleranceSeconds, 10, 3_600);
        toastDurationMs = clamp(toastDurationMs, 1000, 15_000);
        if (toastMode == null) {
            // 老配置只有 true/false 两种，迁移成对应的档位
            toastMode = Boolean.FALSE.equals(toastFeedback) ? ToastMode.SILENT : ToastMode.ALL;
        }
        // 迁移完就把旧字段扔掉，下次保存的 JSON 里不再出现
        toastFeedback = null;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public void save() {
        try {
            Path path = configPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception e) {
            SilentSpaClient.LOGGER.warn("[SPA] 配置写入失败：{}", e.toString());
        }
    }
}
