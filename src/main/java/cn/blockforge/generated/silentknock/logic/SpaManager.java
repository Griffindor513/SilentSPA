package cn.blockforge.generated.silentknock.logic;

import cn.blockforge.generated.silentknock.SilentSpaClient;
import cn.blockforge.generated.silentknock.SpaHooks;
import cn.blockforge.generated.silentknock.config.SpaConfig;
import cn.blockforge.generated.silentknock.spa.FwknopPacket;
import cn.blockforge.generated.silentknock.spa.FwknopRc;
import cn.blockforge.generated.silentknock.spa.SpaKeyException;
import cn.blockforge.generated.silentknock.spa.SpaKeyFile;
import cn.blockforge.generated.silentknock.spa.SpaProfile;
import cn.blockforge.generated.silentknock.spa.SpaSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

/**
 * 把“读密钥 → 造包 → 发 UDP → 延迟 → 回主线程放行原版逻辑”串成一条异步链。
 *
 * <p>线程约定：
 * <ul>
 *   <li>「加入服务器」的入口在渲染主线程（{@code ConnectScreen} 的 Mixin）；</li>
 *   <li>「刷新服务器列表 Ping」的入口在<b>后台线程</b>——1.21.8 原版用「Server Pinger」
 *       线程池调 {@code MultiplayerServerListPinger.add}，所以我们只在那条后台线程上等，
 *       绝不让渲染线程等网络；</li>
 *   <li>文件读取、加密、DNS、socket 全部在 {@link SpaSender#executor()} 里完成；</li>
 *   <li>所有界面 / 声音 / Toast 操作一律通过 {@code MinecraftClient.execute} 回到主线程。</li>
 * </ul>
 */
public final class SpaManager {
    /**
     * 正在走“敲门 → 连接”流程的服务器，用来忽略重复点击。
     *
     * <p>这两个集合会被渲染线程和服务器列表的后台 Ping 线程同时碰，所以都包一层同步。
     */
    private static final Set<ServerInfo> CONNECTING =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    /** 本轮敲门是由模组自己发起的（别再拦一次）。 */
    private static final Set<ServerInfo> BYPASS =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    /** 每个服务器上一次敲门的时刻，按地址记录（服务器列表刷新时用来限流）。 */
    private static final Map<String, Long> LAST_KNOCK = new ConcurrentHashMap<>();

    /**
     * 当前这一次“敲门 → 连接”尝试。连接失败时用它决定断开界面怎么提示；
     * 连上服务器（进了世界）或已经提示过一次就清空，避免误伤后来被踢下线的情况。
     */
    private static volatile PendingConnect PENDING;
    /** 一次连接尝试最多保留多久（毫秒）；超过就当作过期，不再往断开界面上贴提示。 */
    private static final long PENDING_TTL_MS = 180_000L;

    /** 一次连接尝试里，本地敲门这一半的结果。 */
    private static final class PendingConnect {
        final String serverName;
        final long startedAt = System.currentTimeMillis();
        volatile boolean knockFailed;
        volatile Text reason;

        PendingConnect(String serverName) {
            this.serverName = serverName;
        }
    }

    private SpaManager() {
    }

    public static SpaHooks hooks(ServerInfo info) {
        return (SpaHooks) (Object) info;
    }

    public static boolean hasKey(ServerInfo info) {
        if (info == null) {
            return false;
        }
        try {
            String path = hooks(info).silentspa$getKeyPath();
            return path != null && !path.isBlank();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isBypassed(ServerInfo info) {
        return BYPASS.contains(info);
    }

    /** 模组自己重新调用原版方法时用它，避免被自己的 Mixin 再拦一次。 */
    public static void runBypassed(ServerInfo info, Runnable action) {
        BYPASS.add(info);
        try {
            action.run();
        } finally {
            BYPASS.remove(info);
        }
    }

    /** 点击“加入服务器”时是否需要先敲门。 */
    public static boolean shouldKnockOnConnect(ServerInfo info) {
        return SpaConfig.get().enabled && hasKey(info);
    }

    /** 刷新服务器列表时是否需要先敲门（带冷却，避免反复刷新反复敲门）。 */
    public static boolean shouldKnockOnPing(ServerInfo info) {
        if (!shouldKnockOnConnect(info)) {
            return false;
        }
        String key = addressKey(info);
        Long last = LAST_KNOCK.get(key);
        long cooldown = Math.max(0, SpaConfig.get().knockCooldownMs);
        return last == null || System.currentTimeMillis() - last >= cooldown;
    }

    /**
     * 接管一次“加入服务器”。
     *
     * @return true 表示已经接管，调用方需要 cancel 原版 connect
     */
    public static boolean tryHandleConnect(ServerInfo info, Screen parent, MinecraftClient client,
                                           ServerAddress address, boolean quickPlay, CookieStorage cookies) {
        if (isBypassed(info)) {
            // 模组自己重新调进原版 connect，保留当前这次尝试的记录
            return false;
        }
        // 除模组自身重入外，每次新的连接尝试都从干净状态开始，
        // 免得把上一次的敲门结果贴到这一次的断开界面上
        PENDING = null;
        if (info == null || !shouldKnockOnConnect(info)) {
            return false;
        }
        // 顺手对一次时间（有 10 分钟缓存）：连不上时“系统时间不准”是常见原因之一，
        // 断开界面要能把这个原因写出来
        ClockCheck.refreshAsync(true);
        if (!CONNECTING.add(info)) {
            // 同一个服务器已经在敲门，重复点击直接忽略
            return true;
        }
        PendingConnect attempt = new PendingConnect(info.name == null || info.name.isBlank()
                ? hostOf(info) : info.name);
        PENDING = attempt;
        Runnable finish = () -> {
            CONNECTING.remove(info);
            runBypassed(info, () -> ConnectScreen.connect(parent, client, address, info, quickPlay, cookies));
        };
        try {
            knock(info, finish, (failed, reason) -> {
                attempt.knockFailed = failed;
                attempt.reason = reason;
            });
        } catch (Throwable t) {
            CONNECTING.remove(info);
            PENDING = null;
            SilentSpaClient.LOGGER.error("[SPA] 启动敲门失败，回退直连", t);
            finish.run();
        }
        return true;
    }

    /**
     * 接管一次服务器列表 Ping：先把端口敲开，再让原版继续 Ping。
     *
     * <p>1.21.8 的原版在「Server Pinger」后台线程池里调用 {@code add}，所以我们在这里
     * <b>等</b>敲门结果是安全的：被卡住的只是一条后台线程，渲染线程照常画界面。
     *
     * <p>早先的写法是“拦下这次 Ping，等敲门结束后再从渲染线程重新调一次 add”，
     * 结果原版 add 里的 DNS / SRV 解析跑到了渲染线程上，游戏就卡死了。
     * 现在一律不拦、不重新调，只在后台线程上等一下。
     */
    public static void knockBeforePing(ServerInfo info) {
        if (info == null || isBypassed(info)) {
            return;
        }
        if (!shouldKnockOnPing(info)) {
            // 这台不用敲门（没绑密钥），或者刚敲过还在冷却：列表上直接显示 “Pinging”
            if (SpaConfig.get().enabled) {
                ListPhase.pinging(info);
            }
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.isOnThread()) {
            // 渲染线程上绝不等：宁可这次不敲门，也不能把画面卡住
            SilentSpaClient.LOGGER.debug("[SPA] Ping 发生在渲染线程上，跳过同步敲门以免卡画面");
            ListPhase.pinging(info);
            return;
        }
        final SpaConfig cfg = SpaConfig.get();
        final String label = info.name == null || info.name.isBlank() ? hostOf(info) : info.name;
        LAST_KNOCK.put(addressKey(info), System.currentTimeMillis());
        // 列表上这一行的状态字：先 “Knocking”，敲完变 “Pinging”（不再弹右上角的提示框）
        ListPhase.knocking(info);
        final CompletableFuture<Void> chain;
        try {
            // quiet：列表刷新不弹提示框、不响提示音，状态都写在服务器名字右边
            chain = knockChain(info, null, label, null, null, true, true);
        } catch (Throwable t) {
            SilentSpaClient.LOGGER.warn("[SPA] 敲门流程启动失败，直接 Ping：{}", t.toString());
            ListPhase.pinging(info);
            return;
        }
        // 硬上限：无论敲门卡在哪一步，后台 Ping 线程最多等这么久
        long budget = Math.max(2_000L, (long) cfg.delayMs + cfg.udpTimeoutMs + 2_000L);
        try {
            chain.get(budget, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            SilentSpaClient.LOGGER.warn("[SPA] 敲门超时（{} ms），先让服务器列表照常 Ping", budget);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            SilentSpaClient.LOGGER.warn("[SPA] 等待敲门结果失败：{}", t.toString());
        } finally {
            // 不管敲成没敲成，接下来都轮到原版 Ping 了，列表上改成 “Pinging”
            ListPhase.pinging(info);
        }
    }

    /**
     * 玩家刚进入多人游戏列表：把列表里所有 SPA 服务器先敲一遍，敲完再让原版重新 Ping。
     *
     * <p>为什么不等原版自己 Ping：原版第一次 Ping 是在这一行第一次画出来的时候发起的，
     * 那会儿门还没开，列表上只会显示“无法连接”。这里一进来就把门敲开，敲完再把那一行
     * 的状态改回“待 Ping”，原版下一帧就会自己重新 Ping 一次，MOTD 和延迟都是通的。
     *
     * <p>整个方法只做“发出去”这一件事，网络全在后台线程，所以打开列表不会卡。
     * 已经在冷却期内的服务器会跳过，避免来回进出列表反复敲门。
     */
    public static void onServerListOpened(ServerList servers) {
        if (servers == null || !SpaConfig.get().enabled) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        List<ServerInfo> targets = new ArrayList<>();
        int total;
        try {
            total = servers.size();
        } catch (Throwable t) {
            return;
        }
        for (int i = 0; i < total; i++) {
            ServerInfo info;
            try {
                info = servers.get(i);
            } catch (Throwable t) {
                continue;
            }
            if (info == null || isBypassed(info) || !hasKey(info) || !shouldKnockOnPing(info)) {
                continue;
            }
            targets.add(info);
        }
        if (targets.isEmpty()) {
            return;
        }
        SilentSpaClient.LOGGER.info("[SPA] 进入服务器列表，自动敲门 {} 台服务器", targets.size());
        // 不再弹右上角的提示框（服务器一多就刷屏）：每台服务器在自己那一行名字后面
        // 写上 “Knocking”，敲完变 “Pinging”，见 gui.ListStatusLabel
        for (ServerInfo info : targets) {
            autoKnockForList(info, client);
        }
    }

    /** 进列表时对一台服务器自动敲门，敲完让原版重新 Ping 这一行。 */
    private static void autoKnockForList(ServerInfo info, MinecraftClient client) {
        // 先占住限流位：原版下一帧就会给这一行发起 Ping，别让它在我们敲完之前再敲一遍
        LAST_KNOCK.put(addressKey(info), System.currentTimeMillis());
        // 把这一行标成“正在 Ping”：原版就不会在我们敲完之前先去连，
        // 免得门还锁着、列表上刷出一个红色的“无法连接”
        markPinging(info);
        // 列表上这一行的状态字：正在敲门
        ListPhase.knocking(info);
        String label = info.name == null || info.name.isBlank() ? hostOf(info) : info.name;
        try {
            // 静默敲门：状态都写在列表上了，不弹框、不响提示音
            knockChain(info, null, label, () -> requestFreshPing(info, client), null, true, true);
        } catch (Throwable t) {
            SilentSpaClient.LOGGER.warn("[SPA] 列表自动敲门启动失败，改为直接 Ping：{}", t.toString());
            requestFreshPing(info, client);
        }
    }

    /** 把一行服务器标成“正在 Ping”，用原版自己的状态和文案。只在主线程调用。 */
    private static void markPinging(ServerInfo info) {
        info.setStatus(ServerInfo.Status.PINGING);
        info.label = ScreenTexts.EMPTY;
        info.playerCountLabel = ScreenTexts.EMPTY;
    }

    /**
     * 让原版重新 Ping 这一行。
     *
     * <p>把状态改回 {@link ServerInfo.Status#INITIAL} 就行：原版画这一行时看到 INITIAL，
     * 会自己把这次 Ping 交给它的后台线程池——我们不用（也不该）去调 Ping 的内部方法。
     * 列表已经关掉（玩家进了服务器）时什么都不做，免得改到已经没用的状态。
     */
    private static void requestFreshPing(ServerInfo info, MinecraftClient client) {
        Runnable task = () -> {
            if (client.currentScreen instanceof MultiplayerScreen) {
                info.setStatus(ServerInfo.Status.INITIAL);
                info.label = ScreenTexts.EMPTY;
                info.playerCountLabel = ScreenTexts.EMPTY;
                // 敲完门，轮到原版 Ping 了：列表上那行字从 “Knocking” 变成 “Pinging”
                ListPhase.pinging(info);
            }
        };
        if (client.isOnThread()) {
            task.run();
            return;
        }
        try {
            client.execute(task);
        } catch (Throwable t) {
            SilentSpaClient.LOGGER.debug("[SPA] 重新 Ping 排队失败：{}", t.toString());
        }
    }

    /** 核心异步链。{@code afterKnock} 一定会在主线程被调用一次。 */
    public static void knock(ServerInfo info, Runnable afterKnock) {
        knock(info, afterKnock, null);
    }

    /**
     * 核心异步链。
     *
     * @param afterKnock 敲门流程结束后一定会在主线程被调用一次
     * @param onResult   本地敲门结果回调，第一个参数为 true 表示包没发出去；
     *                   可能在网络线程触发，不能在里面碰界面
     */
    public static void knock(ServerInfo info, Runnable afterKnock, BiConsumer<Boolean, Text> onResult) {
        final String label = info.name == null || info.name.isBlank() ? hostOf(info) : info.name;
        LAST_KNOCK.put(addressKey(info), System.currentTimeMillis());
        announceStart(true, label);
        knockChain(info, null, label, afterKnock, onResult, true);
    }

    /**
     * 临时敲门：只请求给定端口，不等延迟、不参与“本次连接尝试”的记录。
     *
     * <p>用在「ESC → SPA 敲门」和给别的模组调用的 API 上：语音模组之类要的额外端口
     * 不用写进 fwknoprc，需要的时候敲一下就行。
     */
    public static void knockExtra(ServerInfo info, List<String> entries, BiConsumer<Boolean, Text> onResult) {
        if (info == null || entries == null || entries.isEmpty()) {
            if (onResult != null) {
                onResult.accept(true, Text.translatable("silent_knock.knock.no_server"));
            }
            return;
        }
        String label = FwknopRc.joinAccess(entries);
        announceStart(false, label);
        knockChain(info, List.copyOf(entries), label, null, onResult, false);
    }

    /** 敲门开始时的提示（聊天栏 + 弹窗 + 音效）。只允许在渲染主线程碰界面，所以统一转过去。 */
    private static void announceStart(boolean waitDelay, String label) {
        runOnMain(() -> {
            if (waitDelay) {
                SpaFeedback.sending(label);
            } else {
                SpaFeedback.extraSending(label);
            }
        });
    }

    /**
     * 把一段界面 / 声音操作排到渲染主线程；本来就在主线程就直接执行。
     *
     * <p>服务器列表 Ping 的入口跑在原版的后台线程上，那里不能直接弹提示、放音效
     * （Toast 列表和声音引擎都不是线程安全的）。
     */
    private static void runOnMain(Runnable action) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.isOnThread()) {
            action.run();
            return;
        }
        try {
            client.execute(action);
        } catch (Throwable t) {
            SilentSpaClient.LOGGER.debug("[SPA] 主线程任务排队失败：{}", t.toString());
        }
    }

    /**
     * 一次敲门的公共异步链。
     *
     * @param customAccess 非 null 表示“只请求这些端口”（临时敲门）；null 表示按当前连的服务器算
     * @param label        聊天栏里显示的标题（服务器名或端口列表）
     * @param waitDelay    是否按配置等 {@code delayMs} 再回调（连服要等；临时敲门不用等）
     * @return 整条链（含最后的延迟）走完就完成的 future；调用方可以按需等待
     */
    private static CompletableFuture<Void> knockChain(ServerInfo info, List<String> customAccess, String label,
                                                      Runnable afterKnock, BiConsumer<Boolean, Text> onResult,
                                                      boolean waitDelay) {
        return knockChain(info, customAccess, label, afterKnock, onResult, waitDelay, false);
    }

    /**
     * 同上，但可以选 {@code quiet}：不弹任何提示、不响提示音，只记日志。
     * 进服务器列表时的批量自动敲门用它，免得服务器一多就刷屏。
     */
    private static CompletableFuture<Void> knockChain(ServerInfo info, List<String> customAccess, String label,
                                                      Runnable afterKnock, BiConsumer<Boolean, Text> onResult,
                                                      boolean waitDelay, boolean quiet) {
        final SpaConfig cfg = SpaConfig.get();
        final String path = hooks(info).silentspa$getKeyPath();
        final String host = hostOf(info);
        // 这次真正要连的游戏端口：加入服务器、刷新 MOTD 都是它
        final int targetPort = serverPortOf(info);
        // 敲门包发往的 UDP 端口：界面里手动指定的最优先，其次配置文件里的 SPA_SERVER_PORT
        final int overrideSpaPort = hooks(info).silentspa$getSpaPort();
        final String username = resolveUsername(cfg);
        final String fallbackAccess = cfg.accessRequest;
        final String fallbackAllowIp = cfg.allowIp;

        if (afterKnock == null) {
            afterKnock = () -> {
            };
        }
        final Runnable ready = afterKnock;
        // 整条链（含放行前的延迟）走完后完成，服务器列表 Ping 靠它决定什么时候继续
        final CompletableFuture<Void> finished = new CompletableFuture<>();

        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        SpaProfile profile = SpaKeyFile.read(Path.of(path));
                        int spaPort = overrideSpaPort > 0 && overrideSpaPort <= 65535
                                ? overrideSpaPort
                                : (profile.serverPort() > 0 ? profile.serverPort() : cfg.spaPort);
                        String access = customAccess != null
                                ? FwknopRc.joinAccess(customAccess)
                                : accessFor(profile, targetPort, fallbackAccess);
                        byte[] payload = FwknopPacket.build(profile.keys(), username,
                                profile.wireAllowIp(fallbackAllowIp), access,
                                profile.digestType(), profile.hmacType(), profile.msgType());
                        return new KnockRequest(payload, spaPort, access);
                    } catch (SpaKeyException e) {
                        throw new CompletionException(e);
                    }
                }, SpaSender.executor())
                .thenCompose(request -> {
                    SilentSpaClient.LOGGER.info("[SPA] 敲门目标 {}:{}，请求打开 {}", host, request.spaPort(), request.access());
                    return SpaSender.send(host, request.spaPort(), request.payload(), cfg.udpTimeoutMs);
                })
                .whenComplete((ignoredResult, error) -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (error != null) {
                        Text reason = describe(error);
                        if (onResult != null) {
                            onResult.accept(true, reason);
                        }
                        SilentSpaClient.LOGGER.warn("[SPA] 敲门失败：{}", reason.getString());
                        runOnMainAndComplete(client, finished, () -> {
                            if (!quiet) {
                                if (waitDelay) {
                                    SpaFeedback.failed(label, reason);
                                } else {
                                    SpaFeedback.extraFailed(reason);
                                }
                            }
                            ready.run();
                        });
                        return;
                    }
                    if (onResult != null) {
                        onResult.accept(false, null);
                    }
                    if (client == null) {
                        // 客户端正在关：不再碰界面，直接放行
                        try {
                            ready.run();
                        } finally {
                            finished.complete(null);
                        }
                        return;
                    }
                    if (!waitDelay) {
                        // 临时敲门：没有要放行的连接，直接回到游戏，让语音模组自己重试
                        SilentSpaClient.LOGGER.info("[SPA] 已向 {} 发送临时敲门包，请求打开 {}", host, label);
                        runOnMainAndComplete(client, finished, () -> {
                            if (!quiet) {
                                SpaFeedback.extraSent(label);
                            }
                            ready.run();
                        });
                        return;
                    }
                    SilentSpaClient.LOGGER.info("[SPA] 已向 {} 发送敲门包，{} ms 后放行连接", host, cfg.delayMs);
                    client.execute(() -> {
                        if (quiet) {
                            return;
                        }
                        SpaFeedback.sent(label, cfg.delayMs);
                        // 语音模组之类的端口是模组自己带上的，告诉玩家一声
                        ExtraKnock.announceAutoKnock(info);
                    });
                    CompletableFuture
                            .delayedExecutor(Math.max(0, cfg.delayMs), TimeUnit.MILLISECONDS, client)
                            .execute(() -> {
                                try {
                                    ready.run();
                                } finally {
                                    finished.complete(null);
                                }
                            });
                });
        return finished;
    }

    /** 在主线程跑一段收尾动作，跑完（成功或抛异常）都把 {@code finished} 完成掉。 */
    private static void runOnMainAndComplete(MinecraftClient client, CompletableFuture<Void> finished,
                                             Runnable action) {
        Runnable guarded = () -> {
            try {
                action.run();
            } finally {
                finished.complete(null);
            }
        };
        if (client == null) {
            guarded.run();
            return;
        }
        try {
            client.execute(guarded);
        } catch (Throwable t) {
            SilentSpaClient.LOGGER.debug("[SPA] 主线程任务排队失败：{}", t.toString());
            finished.complete(null);
        }
    }

    /** 一次敲门要发的包 + 端口 + 请求的访问项。 */
    private record KnockRequest(byte[] payload, int spaPort, String access) {
    }

    /**
     * 组装这次敲门要请求打开的端口。
     *
     * <p>总是包含“这次真正要连的端口”（例如 {@code tcp/25565}），因为加入服务器和刷新 MOTD
     * 用的都是它。另外还会自动带上：
     * <ul>
     *   <li>装了语音模组时的 {@code udp/24454} 或 {@code udp/<游戏端口>}（{@link ExtraKnock#autoAccessEntries(int)}）——
     *       人在连服，门先开好，语音模组随后自己去连就能过；</li>
     *   <li>配置文件里 {@code ACCESS} 写的其它端口（老用法，仍然有效，不是必须的）。</li>
     * </ul>
     */
    private static String accessFor(SpaProfile profile, int targetPort, String fallbackAccess) {
        LinkedHashSet<String> entries = new LinkedHashSet<>();
        String target = FwknopRc.normalizeAccessEntry("tcp/" + targetPort);
        if (target != null) {
            entries.add(target);
        }
        List<String> fromProfile = FwknopRc.splitAccess(profile.access());
        if (fromProfile.isEmpty()) {
            entries.addAll(FwknopRc.splitAccess(fallbackAccess));
        } else {
            entries.addAll(fromProfile);
        }
        // 语音模组之类的额外端口：模组自己知道就一起带上，不用玩家写进配置文件
        entries.addAll(ExtraKnock.autoAccessEntries(targetPort));
        if (entries.isEmpty()) {
            entries.add("tcp/25565");
        }
        return FwknopRc.joinAccess(List.copyOf(entries));
    }

    /** 服务器地址里写的游戏端口，没写就是 25565。 */
    public static int serverPortOf(ServerInfo info) {
        String raw = info == null || info.address == null ? "" : info.address.strip();
        try {
            int port = ServerAddress.parse(raw).getPort();
            if (port >= 1 && port <= 65535) {
                return port;
            }
        } catch (Throwable ignored) {
            // 地址写得不规范就当 25565
        }
        return 25565;
    }

    /** 选中密钥文件后异步预检一次，尽早告诉玩家文件格式对不对。 */
    public static void validateKeyFile(Path path) {
        if (path == null) {
            return;
        }
        SpaSender.supplyAsync(() -> {
            try {
                SpaKeyFile.read(path);
                return null;
            } catch (SpaKeyException e) {
                return e.getText();
            }
        }).thenAccept(error -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return;
            }
            client.execute(() -> {
                if (error == null) {
                    Path name = path.getFileName();
                    SpaFeedback.keyBound(name == null ? path.toString() : name.toString());
                } else {
                    SpaFeedback.keyInvalid(error);
                }
            });
        });
    }

    /** 界面里给这台服务器单独指定的 SPA 端口；没指定返回全局配置值。 */
    public static int portFor(ServerInfo info) {
        int own = hooks(info).silentspa$getSpaPort();
        return own > 0 && own <= 65535 ? own : SpaConfig.get().spaPort;
    }

    public static String hostOf(ServerInfo info) {
        String raw = info.address == null ? "" : info.address.strip();
        try {
            return ServerAddress.parse(raw).getAddress();
        } catch (Throwable ignored) {
            String host = raw;
            int colon = host.lastIndexOf(':');
            if (colon > 0) {
                host = host.substring(0, colon);
            }
            if (host.startsWith("[") && host.endsWith("]")) {
                host = host.substring(1, host.length() - 1);
            }
            return host;
        }
    }

    /** 按地址给服务器做键（列表状态、敲门限流都用它）。同一个地址大小写不同也算同一台。 */
    static String addressKey(ServerInfo info) {
        String address = info.address == null ? "" : info.address.strip();
        return address.toLowerCase(Locale.ROOT);
    }

    private static String resolveUsername(SpaConfig cfg) {
        if (cfg.username != null && !cfg.username.isBlank()) {
            return cfg.username.strip();
        }
        String name = "minecraft";
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getSession() != null && client.getSession().getUsername() != null) {
                name = client.getSession().getUsername();
            }
        } catch (Throwable ignored) {
            // 拿不到正版用户名就用默认值
        }
        name = name.replaceAll("[^A-Za-z0-9_.-]", "");
        return name.isEmpty() ? "minecraft" : name;
    }

    /** 把异常翻译成一句给玩家看的原因（翻译键，跟随游戏语言）。 */
    private static Text describe(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        if (current instanceof SpaKeyException keyError) {
            return keyError.getText();
        }
        if (current instanceof TimeoutException) {
            return Text.translatable("silent_knock.reason.udp_timeout");
        }
        if (current instanceof UnknownHostException) {
            return Text.translatable("silent_knock.reason.unknown_host", textOf(current));
        }
        String message = textOf(current);
        return Text.literal(message);
    }

    private static String textOf(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        message = message.replace('\n', ' ').replace('\r', ' ').strip();
        return message.length() > 120 ? message.substring(0, 120) + "…" : message;
    }

    /** 已经连进服务器（进入世界）了，说明这次敲门 + 连接成功了，清掉记录。 */
    public static void onJoined() {
        PENDING = null;
    }

    /**
     * TCP 握手成功、进入登录阶段：服务器没有拦我们的 IP（要么本来没开 SPA，要么已经放行）。
     *
     * <p>之后就算登录失败（白名单、版本不符、服务器已满……）也跟敲门无关，
     * 所以这里清掉记录，让断开界面保持原版文案。
     */
    public static void onLoginStarted() {
        PENDING = null;
    }

    /**
     * 原版连接失败、要显示“连接失败”界面时调用。
     *
     * <p>只有“这次连接是模组接手敲门的那一次”才改写文案；其它情况（比如玩到一半被踢）
     * 原样返回，不误伤。
     */
    public static Text decorateDisconnect(Text vanillaReason) {
        PendingConnect attempt = PENDING;
        if (attempt == null) {
            return vanillaReason;
        }
        if (System.currentTimeMillis() - attempt.startedAt > PENDING_TTL_MS) {
            PENDING = null;
            return vanillaReason;
        }
        PENDING = null;
        SilentSpaClient.LOGGER.info("[SPA] 敲门后连接失败（{}），改写断开界面提示", attempt.serverName);
        // 连接失败不再弹右上角的框：原因照旧写进这个界面，没必要再提示一次
        return SpaFeedback.decorateDisconnect(vanillaReason, attempt.knockFailed, attempt.reason);
    }
}
