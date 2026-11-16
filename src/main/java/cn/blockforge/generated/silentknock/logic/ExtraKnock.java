package cn.blockforge.generated.silentknock.logic;

import cn.blockforge.generated.silentknock.SilentSpaClient;
import cn.blockforge.generated.silentknock.config.SpaConfig;
import cn.blockforge.generated.silentknock.spa.FwknopRc;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.network.ServerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 临时端口敲门（按需开门）。
 *
 * <p>语音模组、地图模组之类常常还要连服务器上的另一个端口。以前的办法是把这些端口
 * 写进 fwknoprc 的 {@code ACCESS} 行，现在不用了：
 *
 * <ul>
 *   <li><b>自动</b>：连服务器敲门时，如果本机装了已知要额外端口的模组，就把它的端口
 *       （例如 Simple Voice Chat 的 {@code udp/24454}）一起请求，弹一个
 *       “XXX mod detected, knocked xxx automatically” 的提示，人还没进服门就已经开了；</li>
 *   <li><b>按需</b>：还缺别的端口时，游戏里按 ESC →「SPA 敲门」，自己填上
 *       {@code udp/12345} 敲一下，只在这时临时开门，配置文件一个字都不用改。</li>
 * </ul>
 *
 * <p>为什么不能真的“拦住语音模组再敲门”：语音模组的 UDP 连接是它自己开的
 * {@link java.nio.channels.DatagramChannel}，不经过 Minecraft 的网络栈，模组没有办法
 * 在它发包的那一刻插一脚。所以这里用“提前把门开好 + 随时补敲”达到一样的效果。
 */
public final class ExtraKnock {
    /** Simple Voice Chat 的模组 id。 */
    public static final String VOICE_CHAT_MOD_ID = "voicechat";
    /** Simple Voice Chat 服务器端默认的语音端口（UDP）。 */
    public static final int VOICE_CHAT_DEFAULT_PORT = 24454;
    /** Plasmo Voice 的模组 id。 */
    public static final String PLASMO_VOICE_MOD_ID = "plasmovoice";
    /**
     * Dynmap / JourneyMap 网页地图的默认网页端口（TCP，浏览器打开的那个）。
     */
    public static final int WEB_MAP_DEFAULT_PORT = 8123;
    /** BlueMap 的默认网页端口（TCP）。 */
    public static final int BLUEMAP_DEFAULT_PORT = 8100;

    /** 一个检测到的、需要额外端口的模组，以及它对应的访问项。 */
    public record Detected(String modId, String modName, String entry) {
    }

    /** 每个“服务器 + 端口”上次临时敲门的时刻，用来防止连点狂敲。 */
    private static final Map<String, Long> RECENT = new ConcurrentHashMap<>();

    /** 这次游戏里已经提示过“自动敲门”的模组，避免每敲一次都弹一遍。 */
    private static final Set<String> ANNOUNCED = ConcurrentHashMap.newKeySet();

    private ExtraKnock() {
    }

    /**
     * 本机装了哪些已知需要额外端口的模组。
     *
     * @param gamePort 这台服务器的游戏端口，Plasmo Voice 默认就复用这个端口
     */
    public static List<Detected> detectedMods(int gamePort) {
        List<Detected> mods = new ArrayList<>();
        SpaConfig cfg = SpaConfig.get();
        if (!cfg.autoVoicePort) {
            return mods;
        }
        if (isModLoaded(VOICE_CHAT_MOD_ID)) {
            mods.add(new Detected(VOICE_CHAT_MOD_ID, "Simple Voice Chat", "udp/" + VOICE_CHAT_DEFAULT_PORT));
        }
        if (isModLoaded(PLASMO_VOICE_MOD_ID)) {
            // Plasmo Voice 默认端口写 0，也就是“跟服务器游戏端口一样”
            mods.add(new Detected(PLASMO_VOICE_MOD_ID, "Plasmo Voice", "udp/" + clampPort(gamePort)));
        }
        return mods;
    }

    /** 已经装了的额外端口模组对应的访问项；没装就是空表。 */
    public static List<String> autoAccessEntries(int gamePort) {
        List<String> entries = new ArrayList<>();
        for (Detected mod : detectedMods(gamePort)) {
            addNormalized(entries, mod.entry());
        }
        // 进阶玩法：配置里写死几个每次都自动带上的端口（默认空）
        for (String entry : FwknopRc.splitAccess(SpaConfig.get().autoExtraPorts)) {
            addNormalized(entries, entry);
        }
        return entries;
    }

    /** 同上，按服务器地址推游戏端口。 */
    public static List<String> autoAccessEntries(ServerInfo info) {
        return autoAccessEntries(info == null ? 25565 : SpaManager.serverPortOf(info));
    }

    /**
     * 自动敲门成功后弹一次“检测到 XXX 模组，已自动敲门 xxx”的提示。
     *
     * <p>每个模组在一次游戏里只提示一次，免得刷新服务器列表时一直弹。
     * 只能在渲染主线程调用。
     */
    public static void announceAutoKnock(ServerInfo info) {
        List<Detected> mods = detectedMods(info == null ? 25565 : SpaManager.serverPortOf(info));
        for (Detected mod : mods) {
            if (ANNOUNCED.add(mod.modId() + '|' + mod.entry())) {
                SpaFeedback.autoKnockToast(mod.modName(), mod.entry());
            }
        }
    }

    /**
     * 立刻为这个服务器临时敲一次门，只请求给定的端口。
     *
     * <p>调用方可以是主线程，也可以是别的模组；门开之后服务器会放行一小段时间
     * （多久由服务器 fwknopd 的 {@code FW_ACCESS_TIMEOUT} 决定，默认 30 秒）。
     */
    public static boolean knock(ServerInfo info, List<String> requested) {
        if (info == null) {
            SpaFeedback.extraNoServer();
            return false;
        }
        if (!SpaConfig.get().enabled) {
            SilentSpaClient.LOGGER.debug("[SPA] 模组已关闭，忽略临时敲门请求");
            return false;
        }
        List<String> entries = new ArrayList<>();
        for (String raw : requested) {
            for (String entry : FwknopRc.splitAccess(raw)) {
                addNormalized(entries, entry);
            }
        }
        if (entries.isEmpty()) {
            return false;
        }
        String name = info.name == null || info.name.isBlank() ? SpaManager.hostOf(info) : info.name;
        if (!SpaManager.hasKey(info)) {
            SpaFeedback.extraNoKey(name);
            return false;
        }
        long cooldown = Math.max(0, SpaConfig.get().extraPortCooldownMs);
        long now = System.currentTimeMillis();
        if (cooldown > 0) {
            for (String entry : entries) {
                Long last = RECENT.get(keyOf(info, entry));
                if (last != null && now - last < cooldown) {
                    long remain = (cooldown - (now - last) + 999L) / 1000L;
                    SpaFeedback.extraCooldown(entry, Math.max(1L, remain));
                    return false;
                }
            }
        }
        for (String entry : entries) {
            RECENT.put(keyOf(info, entry), now);
        }
        SilentSpaClient.LOGGER.info("[SPA] 临时敲门：{} → {}", name, FwknopRc.joinAccess(entries));
        SpaManager.knockExtra(info, List.copyOf(entries), null);
        return true;
    }

    /** 把 {@code tcp/25565} 之类的写法规范化后加进列表（去重、保持顺序）。 */
    private static void addNormalized(List<String> target, String raw) {
        String entry = FwknopRc.normalizeAccessEntry(raw);
        if (entry != null && !target.contains(entry)) {
            target.add(entry);
        }
    }

    private static int clampPort(int port) {
        return port >= 1 && port <= 65535 ? port : 25565;
    }

    private static String keyOf(ServerInfo info, String entry) {
        String address = info.address == null ? "" : info.address.strip().toLowerCase(Locale.ROOT);
        return address + '|' + entry;
    }

    private static boolean isModLoaded(String id) {
        try {
            return FabricLoader.getInstance().isModLoaded(id);
        } catch (Throwable t) {
            return false;
        }
    }
}
