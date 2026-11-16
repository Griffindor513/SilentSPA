package cn.blockforge.generated.silentknock.config;

import cn.blockforge.generated.silentknock.SilentSpaClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 「直接连接」界面按服务器地址记住的密钥绑定，存在 {@code config/silent_knock_direct.json}。
 *
 * <p>为什么要单独记一份：原版每次点「直接连接」都会 <b>新建一个空的服务器条目</b>
 * （{@code MultiplayerScreen} 里 {@code new ServerInfo("Minecraft Server", "", OTHER)}），
 * 界面关掉再打开，绑在上面的东西自然就没了——地址框里的 IP 只是从
 * {@code options.lastServer} 读回来的文字，跟绑定无关。
 * 所以这里按地址把绑定落盘，下次打开这个地址时再填回去，
 * 免得出现“界面上写着未绑定、点连接却照样敲门”的怪事。
 *
 * <p>文件里只存 <b>密钥文件的绝对路径</b> 和可选端口，密钥内容永远不落盘。
 */
public final class DirectBindings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** 最多记多少个地址，防止文件无限长大（新的挤掉最旧的）。 */
    private static final int MAX_ENTRIES = 64;
    private static final Object LOCK = new Object();

    /** 还没读过盘时为 null。读写都在 {@link #LOCK} 里做。 */
    private static Map<String, Binding> bindings;

    private DirectBindings() {
    }

    /** 一个地址记住的东西：用哪份密钥文件 + 敲门发往哪个 UDP 端口（&lt;= 0 表示用全局配置）。 */
    public static final class Binding {
        public String keyPath;
        public int port = -1;

        public Binding() {
        }

        public Binding(String keyPath, int port) {
            this.keyPath = keyPath;
            this.port = port;
        }

        public boolean isBound() {
            return this.keyPath != null && !this.keyPath.isBlank();
        }
    }

    /** 这份记忆的落盘位置。 */
    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("silent_knock_direct.json");
    }

    /** 取某个地址记住的绑定；没有就返回 null。 */
    public static Binding get(String address) {
        String key = normalize(address);
        if (key.isEmpty()) {
            return null;
        }
        synchronized (LOCK) {
            Binding found = load().get(key);
            return found == null || !found.isBound() ? null : found;
        }
    }

    /** 记住某个地址用哪份密钥文件；{@code keyPath} 为空等于解绑。 */
    public static void put(String address, String keyPath, int port) {
        String key = normalize(address);
        if (key.isEmpty()) {
            return;
        }
        if (keyPath == null || keyPath.isBlank()) {
            remove(address);
            return;
        }
        synchronized (LOCK) {
            Map<String, Binding> map = load();
            map.remove(key);
            map.put(key, new Binding(keyPath.strip(), port > 0 && port <= 65535 ? port : -1));
            while (map.size() > MAX_ENTRIES) {
                String oldest = map.keySet().iterator().next();
                map.remove(oldest);
            }
            save(map);
        }
    }

    /** 忘掉某个地址的绑定（密钥文件本身不动）。 */
    public static void remove(String address) {
        String key = normalize(address);
        if (key.isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            Map<String, Binding> map = load();
            if (map.remove(key) == null) {
                return;
            }
            save(map);
        }
    }

    /** 统一比较口径：去掉首尾空格、转小写（端口号大小写无关，主机名本来也不区分大小写）。 */
    private static String normalize(String address) {
        return address == null ? "" : address.strip().toLowerCase(Locale.ROOT);
    }

    private static Map<String, Binding> load() {
        if (bindings != null) {
            return bindings;
        }
        Map<String, Binding> map = new LinkedHashMap<>();
        Path path = file();
        try {
            if (Files.isRegularFile(path)) {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                BindingsFile parsed = GSON.fromJson(json, BindingsFile.class);
                if (parsed != null && parsed.bindings != null) {
                    for (Map.Entry<String, Binding> entry : parsed.bindings.entrySet()) {
                        Binding value = entry.getValue();
                        if (entry.getKey() != null && value != null && value.isBound()) {
                            map.put(entry.getKey(), value);
                        }
                    }
                }
            }
        } catch (JsonSyntaxException e) {
            SilentSpaClient.LOGGER.warn("[SPA] 直连绑定记录格式不对，当作空的重新开始：{}", e.toString());
            map.clear();
        } catch (Exception e) {
            SilentSpaClient.LOGGER.warn("[SPA] 直连绑定记录读取失败：{}", e.toString());
        }
        bindings = map;
        return map;
    }

    private static void save(Map<String, Binding> map) {
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            BindingsFile out = new BindingsFile();
            out.bindings = map;
            Files.writeString(path, GSON.toJson(out), StandardCharsets.UTF_8);
        } catch (Exception e) {
            SilentSpaClient.LOGGER.warn("[SPA] 直连绑定记录写入失败：{}", e.toString());
        }
    }

    /** 文件外形：{@code {"bindings": {"play.example.com:25565": {...}}}}。 */
    private static final class BindingsFile {
        public Map<String, Binding> bindings = new LinkedHashMap<>();
    }
}
