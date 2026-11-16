package cn.blockforge.generated.silentknock.logic;

import cn.blockforge.generated.silentknock.config.SpaConfig;
import net.minecraft.client.network.ServerInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务器列表里每一行现在处在哪一步：正在敲门（Knocking）还是正在 Ping（Pinging）。
 *
 * <p>玩家在列表里看到的「Minecraft Server &nbsp;Knocking」那行字就是从这儿来的。
 * 以前敲门会弹一个右上角的提示框，进一次列表、刷一次列表就弹一次，太闹；
 * 现在改成把状态直接写在服务器名字右边，进门前后一眼就能看出来。
 *
 * <p>线程：写的人可能是服务器列表的后台 Ping 线程，读的人是渲染线程，
 * 所以底下用 {@link ConcurrentHashMap}；每个状态都带一个过期时间，
 * 万一哪一步没走到（关界面、断网、超时），过一会儿也会自己消失，不会一直挂着「Knocking」。
 */
public final class ListPhase {
    /** 一行服务器此刻在做的事。 */
    public enum Phase {
        /** 正在发敲门包（等服务器把门打开）。 */
        KNOCKING,
        /** 门已经（或不用）敲了，正在等服务器回 MOTD。 */
        PINGING
    }

    /** 地址 → 状态。同一个地址重复进来会覆盖旧状态。 */
    private static final Map<String, State> STATES = new ConcurrentHashMap<>();

    /** Ping 状态最多挂多久（毫秒）：原版 Ping 一般一两秒就出结果，留足余量。 */
    private static final long PING_TTL_MS = 15_000L;
    /** 敲门状态在配置的等待时间之外再多留几秒，防止包卡住时一直显示 Knocking。 */
    private static final long KNOCK_GRACE_MS = 5_000L;

    private ListPhase() {
    }

    /** 这一行开始敲门了。 */
    public static void knocking(ServerInfo info) {
        put(info, Phase.KNOCKING);
    }

    /** 这一行开始 Ping 了（或者压根不用敲门，直接 Ping）。 */
    public static void pinging(ServerInfo info) {
        put(info, Phase.PINGING);
    }

    /**
     * 这一行现在处在哪一步；没记录、已过期都返回 {@code null}（当作「不知道」）。
     */
    public static Phase phaseOf(ServerInfo info) {
        if (info == null) {
            return null;
        }
        String key = SpaManager.addressKey(info);
        State state = STATES.get(key);
        if (state == null) {
            return null;
        }
        if (System.currentTimeMillis() > state.expiresAt()) {
            STATES.remove(key, state);
            return null;
        }
        return state.phase();
    }

    /** 这一行已经 ping 出结果（连上了 / 连不上），把状态清掉。 */
    public static void clear(ServerInfo info) {
        if (info != null) {
            STATES.remove(SpaManager.addressKey(info));
        }
    }

    private static void put(ServerInfo info, Phase phase) {
        if (info == null) {
            return;
        }
        STATES.put(SpaManager.addressKey(info), new State(phase, System.currentTimeMillis() + ttlOf(phase)));
    }

    private static long ttlOf(Phase phase) {
        if (phase == Phase.PINGING) {
            return PING_TTL_MS;
        }
        SpaConfig cfg = SpaConfig.get();
        return KNOCK_GRACE_MS + Math.max(0, cfg.delayMs) + Math.max(0, cfg.udpTimeoutMs);
    }

    /** 一条状态 + 它的过期时刻（毫秒时间戳）。 */
    private record State(Phase phase, long expiresAt) {
    }
}
