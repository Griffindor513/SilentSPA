package cn.blockforge.generated.silentknock.logic;

import cn.blockforge.generated.silentknock.SilentSpaClient;
import cn.blockforge.generated.silentknock.config.SpaConfig;
import cn.blockforge.generated.silentknock.spa.SpaSender;
import net.minecraft.client.MinecraftClient;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 本地系统时间校验：防止电脑时钟偏差太大，敲门包被服务器直接丢掉。
 *
 * <p>为什么需要：fwknop 的 SPA 包正文里带一个<b>时间戳</b>，fwknopd 会拿它和服务器
 * 自己的时间比，偏差超过容差（默认 120 秒）就把包当重放包丢掉。丢包是静默的，
 * 客户端只会看到“连接失败”，很难想到是电脑时间不对。
 *
 * <p>怎么校验：向公共 NTP 服务器发一个标准的 SNTP 请求（UDP 123，48 字节），
 * 从回包里取出服务器时间，和本机时间相减得到偏差。整个过程都在模组的后台线程池里
 * 跑，带 2 秒超时；拿不到时间就当作“没检查过”，绝不乱报。
 *
 * <p>结果只用来<b>提醒</b>：提示弹窗、以及连接失败原因里多写一句“系统时间不准”，
 * 不会偷偷改本机时间，也不会伪造包里的时间戳。
 */
public final class ClockCheck {
    /** 1900-01-01 到 1970-01-01 的毫秒数，NTP 时间戳的纪元换算用。 */
    private static final long NTP_TO_UNIX_MS = 2_208_988_800_000L;
    /** 检查成功后再过多久重新检查（毫秒）。 */
    private static final long RECHECK_MS = 10 * 60_000L;
    /** 上一次检查失败（比如没网 / 123 端口被封）后过多久再试（毫秒）。 */
    private static final long RETRY_MS = 3 * 60_000L;
    /** 单次 NTP 请求超时（毫秒）。 */
    private static final int NTP_TIMEOUT_MS = 2_000;

    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean();
    private static volatile boolean checked;
    private static volatile long skewMs;
    private static volatile long lastAttemptAt;
    /** 这个游戏会话里是不是已经提醒过“时间不准”了，避免反复弹。 */
    private static volatile boolean warned;

    private ClockCheck() {
    }

    /** 配置里开了时间校验没有。 */
    public static boolean enabled() {
        return SpaConfig.get().timeCheck;
    }

    /** 已经成功拿到过网络时间（也就是 {@link #isSkewed()} 的结果可信）。 */
    public static boolean isChecked() {
        return checked;
    }

    /** 本机时间比网络时间快多少毫秒（负数表示本机慢了）。 */
    public static long skewMillis() {
        return skewMs;
    }

    /** 配置里的容差（毫秒），fwknop 默认是 120 秒。 */
    public static long toleranceMillis() {
        return Math.max(1, SpaConfig.get().timeToleranceSeconds) * 1000L;
    }

    /** 本机时间和网络时间的偏差是不是超过了容差。 */
    public static boolean isSkewed() {
        return enabled() && checked && Math.abs(skewMs) > toleranceMillis();
    }

    /** 偏差多少秒（四舍五入后的绝对值，给文案用）。 */
    public static long skewSeconds() {
        return Math.round(Math.abs(skewMs) / 1000.0D);
    }

    /**
     * 异步检查一次本机时间，必要时提醒玩家。
     *
     * @param warn true 表示偏差太大时弹提示；false 只更新结果
     */
    public static void refreshAsync(boolean warn) {
        if (!enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = lastAttemptAt;
        if (last > 0) {
            long wait = checked ? RECHECK_MS : RETRY_MS;
            if (now - last < wait) {
                if (warn) {
                    warnIfNeeded();
                }
                return;
            }
        }
        if (!IN_FLIGHT.compareAndSet(false, true)) {
            return;
        }
        lastAttemptAt = now;
        SpaSender.supplyAsync(ClockCheck::measure).whenComplete((measured, error) -> {
            IN_FLIGHT.set(false);
            if (error != null || measured == null || measured == Long.MIN_VALUE) {
                SilentSpaClient.LOGGER.debug("[SPA] 时间校验没拿到网络时间（可能是 123 端口被封），跳过：{}",
                        error == null ? "无响应" : error.toString());
                return;
            }
            skewMs = measured;
            checked = true;
            if (isSkewed()) {
                SilentSpaClient.LOGGER.warn("[SPA] 本机时间与网络时间相差 {} 秒（容差 {} 秒），敲门包可能被服务器丢弃",
                        skewSeconds(), Math.max(1, SpaConfig.get().timeToleranceSeconds));
            } else {
                SilentSpaClient.LOGGER.info("[SPA] 时间校验通过，本机与网络时间相差 {} 秒", skewSeconds());
            }
            // 不管这次是不是“要求提醒”的检查，结果出来了都可以提醒；
            // warnIfNeeded 自己会保证一个会话只提醒一次
            warnIfNeeded();
        });
    }

    /** 偏差太大就提醒一次（每个游戏会话只提醒一次）。 */
    public static void warnIfNeeded() {
        if (!isSkewed() || warned) {
            return;
        }
        // 客户端还没起来（比如刚开机初始化）就先不提醒，等下次进入多人游戏列表再说，
        // 免得把“已提醒”的标记白占掉、玩家什么都没看到
        if (MinecraftClient.getInstance() == null) {
            return;
        }
        warned = true;
        long tolerance = Math.max(1, SpaConfig.get().timeToleranceSeconds);
        long seconds = skewSeconds();
        boolean ahead = skewMs > 0;
        SpaFeedback.clockSkew(seconds, tolerance, ahead);
    }

    /** 依次试配置里的服务器和几个公共 NTP 主机，拿到第一个能用的偏差。 */
    private static long measure() {
        List<String> hosts = new ArrayList<>();
        String configured = SpaConfig.get().timeServer;
        if (configured != null && !configured.isBlank()) {
            hosts.add(configured.strip());
        }
        // 配置里那台不通时再试两个大厂公共 NTP，都不通就当这次没检查
        hosts.add("time.cloudflare.com");
        hosts.add("time.google.com");
        for (String host : hosts) {
            try {
                return querySkew(host);
            } catch (Exception e) {
                SilentSpaClient.LOGGER.debug("[SPA] 向 {} 校时失败：{}", host, e.toString());
            }
        }
        return Long.MIN_VALUE;
    }

    /**
     * 向一台 NTP 服务器发 SNTP 请求并算出偏差。
     *
     * @return 服务器时间 - 本机时间（毫秒）
     */
    private static long querySkew(String host) throws Exception {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(NTP_TIMEOUT_MS);
            byte[] request = new byte[48];
            // LI=0, VN=3, Mode=3（客户端），其余字段留 0
            request[0] = 0x1B;
            InetAddress address = InetAddress.getByName(host);
            long sentAt = System.currentTimeMillis();
            socket.send(new DatagramPacket(request, request.length, address, 123));
            byte[] response = new byte[48];
            DatagramPacket packet = new DatagramPacket(response, response.length);
            socket.receive(packet);
            long receivedAt = System.currentTimeMillis();
            if (packet.getLength() < 48) {
                throw new IllegalStateException("NTP 响应太短");
            }
            long seconds = readUnsignedInt(response, 40);
            long fraction = readUnsignedInt(response, 44);
            if (seconds == 0L) {
                throw new IllegalStateException("NTP 响应没有时间戳");
            }
            long serverMs = (seconds - NTP_TO_UNIX_MS / 1000L) * 1000L
                    + fraction * 1000L / 4_294_967_296L;
            // 用发和收的中间点当本机时间，粗略抵掉一半往返延迟；2 分钟的容差够用了
            long localMs = (sentAt + receivedAt) / 2L;
            return serverMs - localMs;
        }
    }

    private static long readUnsignedInt(byte[] data, int offset) {
        return ((long) (data[offset] & 0xFF) << 24)
                | ((long) (data[offset + 1] & 0xFF) << 16)
                | ((long) (data[offset + 2] & 0xFF) << 8)
                | (long) (data[offset + 3] & 0xFF);
    }
}
