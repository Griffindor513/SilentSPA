package cn.blockforge.generated.silentknock.spa;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标准 {@code fwknoprc} 配置段的读写。
 *
 * <p>读的时候尽量宽容：真的 {@code ~/.fwknoprc}、fwknop-gui 存出来的文件、
 * {@code fwknop --key-gen} 的两行输出、以及本模组旧版本写的 {@code .spa} 文本都能读；
 * 写的时候严格按 fwknop 手册里的指令名输出，保证生成的文件能直接给 fwknop 客户端用。
 *
 * <p>支持的指令（见 {@code fwknop(8)} 的 FWKNOPRC FILE 一节）：
 * {@code SPA_SERVER}、{@code SPA_SERVER_PORT}、{@code SPA_SERVER_PROTO}、
 * {@code ACCESS}、{@code ALLOW_IP}、{@code KEY}/{@code KEY_BASE64}、
 * {@code HMAC_KEY}/{@code HMAC_KEY_BASE64}、{@code DIGEST_TYPE}、{@code HMAC_DIGEST_TYPE}。
 * {@code USE_GPG Y} 的配置目前不支持（本模组只做 HMAC 模式），会给出明确提示。
 */
public final class FwknopRc {
    /** {@code [section]} 段名。 */
    private static final Pattern SECTION_LINE = Pattern.compile("^\\[(.+)]$");
    /** {@code KEY value}、{@code KEY: value}、{@code KEY = value} 三种写法都认。 */
    private static final Pattern KEY_VALUE =
            Pattern.compile("^([A-Za-z][A-Za-z0-9_.-]*)\\s*(?:[:=]\\s*|\\s+)(.+)$");
    /** 裸 Base64 行（旧格式 / fwknop --key-gen 输出）。 */
    private static final Pattern BASE64_LINE = Pattern.compile("^[A-Za-z0-9+/=]{8,}$");
    /** {@code ACCESS} 里的一项：tcp/22、udp/24454、tcp/8000-8100。 */
    private static final Pattern ACCESS_ENTRY =
            Pattern.compile("^([A-Za-z]+)\\s*/\\s*(\\d{1,5})(?:\\s*-\\s*(\\d{1,5}))?$");
    /** Y/N 之类的布尔值。 */
    private static final Pattern BOOLEAN = Pattern.compile("(?i)^(y|n|yes|no|true|false|on|off|1|0)$");

    /** fwknop 支持的摘要算法。 */
    private static final List<String> DIGEST_TYPES = List.of("md5", "sha1", "sha256", "sha384", "sha512");

    private FwknopRc() {
    }

    /** 一段解析出来还没用上的原始键值。 */
    private static final class Stanza {
        final String name;
        final Map<String, String> values = new LinkedHashMap<>();

        Stanza(String name) {
            this.name = name;
        }

        boolean hasKeyLikeValue() {
            for (String key : values.keySet()) {
                if (key.equals("KEY") || key.equals("KEY_BASE64")
                        || key.equals("HMAC_KEY") || key.equals("HMAC_KEY_BASE64")
                        || key.equals("RIJNDAEL_KEY") || key.equals("RIJNDAEL_KEY_BASE64")) {
                    return true;
                }
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ 读

    public static SpaProfile parse(List<String> lines) throws SpaKeyException {
        List<Stanza> stanzas = new ArrayList<>();
        List<byte[]> bare = new ArrayList<>();
        Stanza current = null;

        for (String raw : lines) {
            String line = raw == null ? "" : raw.strip();
            if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
                line = line.substring(1).strip();
            }
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";") || line.startsWith("//")) {
                continue;
            }
            Matcher section = SECTION_LINE.matcher(line);
            if (section.matches()) {
                current = new Stanza(section.group(1).strip());
                stanzas.add(current);
                continue;
            }
            Matcher kv = KEY_VALUE.matcher(line);
            if (kv.matches()) {
                if (current == null) {
                    current = new Stanza("");
                    stanzas.add(current);
                }
                String key = normalizeKey(kv.group(1));
                String value = kv.group(2).strip();
                if (!value.isEmpty()) {
                    current.values.put(key, value);
                }
                continue;
            }
            if (BASE64_LINE.matcher(line).matches()) {
                bare.add(decodeBase64(line, "Base64"));
            }
        }

        Stanza chosen = pickStanza(stanzas);
        return build(chosen, bare);
    }

    /** 多段文件里挑一段：优先两把密钥都齐的，其次有密钥的，最后第一段。 */
    private static Stanza pickStanza(List<Stanza> stanzas) {
        Stanza firstWithKeys = null;
        for (Stanza stanza : stanzas) {
            if (stanza.values.containsKey("KEY") && stanza.values.containsKey("HMAC_KEY")) {
                return stanza;
            }
            if (firstWithKeys == null && stanza.hasKeyLikeValue()) {
                firstWithKeys = stanza;
            }
        }
        if (firstWithKeys != null) {
            return firstWithKeys;
        }
        return stanzas.isEmpty() ? new Stanza("") : stanzas.get(0);
    }

    private static SpaProfile build(Stanza stanza, List<byte[]> bare) throws SpaKeyException {
        byte[] encryption = null;
        byte[] hmac = null;
        String server = "";
        String proto = "udp";
        String digest = null;
        String hmacDigest = null;
        String allowIp = null;
        String access = "";
        int port = 0;
        int msgType = SpaProfile.MSG_TYPE_OPEN_PORT;
        boolean gpg = false;

        for (Map.Entry<String, String> entry : stanza.values.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            switch (key) {
                case "KEY" -> encryption = value.getBytes(StandardCharsets.UTF_8);
                case "KEY_BASE64", "RIJNDAEL_KEY_BASE64" ->
                        encryption = BOOLEAN.matcher(value).matches() ? encryption : decodeBase64(value, "KEY_BASE64");
                case "RIJNDAEL_KEY", "ENCRYPT_KEY", "SPA_SERVER_KEY" ->
                        encryption = value.getBytes(StandardCharsets.UTF_8);
                case "HMAC_KEY" -> hmac = value.getBytes(StandardCharsets.UTF_8);
                case "HMAC_KEY_BASE64" ->
                        hmac = BOOLEAN.matcher(value).matches() ? hmac : decodeBase64(value, "HMAC_KEY_BASE64");
                case "USE_HMAC" -> {
                    // 写了 HMAC 密钥就说明是 HMAC 模式，这里不用特别处理
                }
                case "DIGEST_TYPE" -> digest = value;
                case "HMAC_DIGEST_TYPE", "HMAC_TYPE" -> hmacDigest = value;
                case "ALLOW_IP" -> allowIp = value;
                case "ACCESS", "ACCESS_PORTS" -> access = value;
                case "MSG_TYPE" -> msgType = parseInt(value, SpaProfile.MSG_TYPE_OPEN_PORT);
                case "SPA_SERVER", "SERVER" -> server = value;
                case "PORT", "SERVER_PORT", "SPA_SERVER_PORT" -> port = parseInt(value, 0);
                case "SPA_SERVER_PROTO", "PROTO" -> proto = value;
                case "USE_GPG" -> gpg = gpg || isTrue(value);
                default -> {
                    // 其它指令（FW_TIMEOUT、SPA_SOURCE_PORT……）敲门时用不到，忽略
                }
            }
        }

        if (gpg) {
            throw new SpaKeyException("silent_knock.key.gpg_unsupported");
        }

        // 旧的两行裸 Base64 格式：第一行 HMAC，第二行加密密钥
        if (hmac == null && !bare.isEmpty()) {
            hmac = bare.remove(0);
        }
        if (encryption == null && !bare.isEmpty()) {
            encryption = bare.remove(0);
        }

        if (encryption == null) {
            throw new SpaKeyException("silent_knock.key.missing_encryption");
        }
        if (hmac == null) {
            throw new SpaKeyException("silent_knock.key.missing_hmac");
        }
        if (encryption.length < 1 || encryption.length > 64) {
            throw new SpaKeyException("silent_knock.key.bad_encryption_length", encryption.length);
        }
        if (hmac.length < 1 || hmac.length > 256) {
            throw new SpaKeyException("silent_knock.key.bad_hmac_length", hmac.length);
        }

        String accessList = sanitizeAccess(access);
        return new SpaProfile(stanza.name, server, port, proto, new SpaKeyMaterial(encryption, hmac),
                normalizeDigest(digest), normalizeDigest(hmacDigest), allowIp, msgType, accessList);
    }

    // ------------------------------------------------------------------ 写

    /** 按 fwknop 手册的指令名生成一个可以直接用的 fwknoprc 配置段。 */
    public static String render(SpaProfile profile) {
        StringBuilder out = new StringBuilder(640);
        out.append("# 由 SilentSPA 生成 / Generated by SilentSPA\n");
        out.append("# 这是标准的 fwknoprc 配置段，也能直接给 fwknop 客户端用。\n");
        out.append("# 段里这两把密钥必须和服务器端 fwknopd 的配置完全一致。\n");
        out.append('[').append(cleanSection(profile.section())).append("]\n");
        if (!profile.server().isBlank()) {
            line(out, "SPA_SERVER", profile.server());
        }
        int port = profile.serverPort() > 0 ? profile.serverPort() : SpaProfile.DEFAULT_SPA_PORT;
        line(out, "SPA_SERVER_PORT", Integer.toString(port));
        line(out, "SPA_SERVER_PROTO", "udp");
        line(out, "USE_HMAC", "Y");
        line(out, "KEY_BASE64", encodeBase64(profile.keys().encryptionKey()));
        line(out, "HMAC_KEY_BASE64", encodeBase64(profile.keys().hmacKey()));
        line(out, "DIGEST_TYPE", profile.digestType());
        line(out, "HMAC_DIGEST_TYPE", profile.hmacType());
        line(out, "ALLOW_IP", profile.allowIp().isBlank() ? SpaProfile.ALLOW_IP_SOURCE : profile.allowIp());
        String access = joinAccess(splitAccess(profile.access()));
        if (!access.isEmpty()) {
            line(out, "ACCESS", access);
        }
        return out.toString();
    }

    private static void line(StringBuilder out, String key, String value) {
        out.append(key);
        for (int i = key.length(); i < 24; i++) {
            out.append(' ');
        }
        out.append(value).append('\n');
    }

    private static String cleanSection(String section) {
        String cleaned = section == null ? "" : section.strip().replaceAll("[\\[\\]\\r\\n]", "");
        return cleaned.isEmpty() ? "default" : cleaned;
    }

    // ------------------------------------------------------------------ 小工具

    /** 把 {@code SPA_SERVER_KEY} 这类带前缀的写法统一成 {@code KEY}。 */
    private static String normalizeKey(String raw) {
        String key = raw.strip().toUpperCase(Locale.ROOT).replace('-', '_');
        if (key.startsWith("SPA_SERVER_") && key.length() > "SPA_SERVER_".length()) {
            key = key.substring("SPA_SERVER_".length());
        }
        return key;
    }

    private static String normalizeDigest(String value) throws SpaKeyException {
        if (value == null || value.isBlank()) {
            return SpaProfile.DEFAULT_DIGEST_TYPE;
        }
        String digest = value.strip().toLowerCase(Locale.ROOT).replace("-", "");
        if (!DIGEST_TYPES.contains(digest)) {
            throw new SpaKeyException("silent_knock.key.bad_type", value);
        }
        return digest;
    }

    /** 把 {@code ACCESS} 里合法的项挑出来、统一格式；全都不合法时给明确的错误。 */
    private static String sanitizeAccess(String raw) throws SpaKeyException {
        String access = raw == null ? "" : raw.strip();
        if (access.isEmpty()) {
            return "";
        }
        List<String> entries = splitAccess(access);
        if (entries.isEmpty()) {
            throw new SpaKeyException("silent_knock.key.bad_access", raw);
        }
        return String.join(",", entries);
    }

    /** 拆 {@code ACCESS} 列表，丢掉不认识的项。 */
    public static List<String> splitAccess(String access) {
        List<String> entries = new ArrayList<>();
        if (access == null) {
            return entries;
        }
        for (String part : access.split("[,;\\s]+")) {
            String entry = normalizeAccessEntry(part);
            if (entry != null && !entries.contains(entry)) {
                entries.add(entry);
            }
        }
        return entries;
    }

    /** 规范化单项 {@code tcp/25565}，认不出来返回 null。 */
    public static String normalizeAccessEntry(String raw) {
        if (raw == null) {
            return null;
        }
        String entry = raw.strip();
        if (entry.isEmpty()) {
            return null;
        }
        Matcher matcher = ACCESS_ENTRY.matcher(entry);
        if (!matcher.matches()) {
            return null;
        }
        String proto = matcher.group(1).toLowerCase(Locale.ROOT);
        if (!proto.equals("tcp") && !proto.equals("udp") && !proto.equals("icmp")) {
            return null;
        }
        int from = parseInt(matcher.group(2), -1);
        int to = matcher.group(3) == null ? from : parseInt(matcher.group(3), -1);
        if (from < 1 || from > 65535 || to < from || to > 65535) {
            return null;
        }
        return matcher.group(3) == null ? proto + "/" + from : proto + "/" + from + "-" + to;
    }

    /** 拼成 fwknop 的 {@code ACCESS} 字符串（去重、保持顺序）。 */
    public static String joinAccess(List<String> entries) {
        return String.join(",", entries);
    }

    public static byte[] decodeBase64(String value, String what) throws SpaKeyException {
        String cleaned = value.replaceAll("\\s+", "");
        int rem = cleaned.length() % 4;
        if (rem == 2) {
            cleaned = cleaned + "==";
        } else if (rem == 3) {
            cleaned = cleaned + "=";
        } else if (rem != 0) {
            throw new SpaKeyException("silent_knock.key.not_base64", what);
        }
        try {
            return Base64.getDecoder().decode(cleaned);
        } catch (IllegalArgumentException e) {
            throw new SpaKeyException("silent_knock.key.not_base64", what);
        }
    }

    public static String encodeBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static boolean isTrue(String value) {
        if (value == null) {
            return false;
        }
        String text = value.strip();
        return text.equalsIgnoreCase("y") || text.equalsIgnoreCase("yes")
                || text.equalsIgnoreCase("true") || text.equals("1") || text.equalsIgnoreCase("on");
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.strip());
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
