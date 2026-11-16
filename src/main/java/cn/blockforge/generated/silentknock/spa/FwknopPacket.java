package cn.blockforge.generated.silentknock.spa;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

/**
 * 按 fwknopd 的标准 SPA 报文格式构造 UDP 载荷（--use-hmac 模式）。
 *
 * <p>线上格式与 libfko 完全一致：
 * <pre>
 * 明文 = 随机值:Base64(用户名):时间戳:协议版本:消息类型:Base64(访问请求):Base64(摘要)
 * 密钥 = OpenSSL EVP_BytesToKey(MD5, 1 轮, 加密密钥, 8 字节随机盐) 派生 32 字节 AES 密钥 + 16 字节 IV
 * 包体 = "Salted__" + 盐 + AES-256-CBC-PKCS7(明文)
 * 签名 = HMAC(完整 Base64 包体, HMAC 密钥)   // 默认 HMAC-SHA256
 * 发送 = Base64(包体) + Base64(签名)，然后去掉 Base64 里固定的前 10 个字符 "U2FsdGVkX1"
 * </pre>
 *
 * <p>摘要算法（{@code DIGEST_TYPE}）和 HMAC 算法（{@code HMAC_DIGEST_TYPE}）跟着
 * fwknoprc 配置走，默认都是 SHA256；fwknop 2.5 之后的默认值也是 SHA256。
 *
 * <p>最后去掉前 10 个字符是 fwknop 的固定行为（fwknopd 收到后会自己补回去），
 * 目的是让每个包的前缀不一样。
 */
public final class FwknopPacket {
    /** libfko 的协议版本字符串，长度受 MAX_SPA_VERSION_SIZE=8 限制。 */
    public static final String PROTOCOL_VERSION = "3.0.0";
    /** FKO_ACCESS_MSG = 1，打开端口。 */
    public static final int MSG_TYPE_ACCESS = SpaProfile.MSG_TYPE_OPEN_PORT;

    private static final byte[] SALTED_PREFIX = "Salted__".getBytes(StandardCharsets.US_ASCII);
    private static final String B64_SALT_PREFIX = "U2FsdGVkX1";
    private static final int KEY_LEN = 32;
    private static final int IV_LEN = 16;
    private static final int SALT_LEN = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private FwknopPacket() {
    }

    /**
     * 造一个 SPA 包。
     *
     * @param allowIp fwknop 报文里的 allow IP，{@code 0.0.0.0} 表示让服务器用包源地址
     * @param access  要打开的端口列表，例如 {@code tcp/25565,udp/24454}
     */
    public static byte[] build(SpaKeyMaterial keys, String username, String allowIp, String access,
                               String digestType, String hmacType, int msgType) {
        byte[] salt = new byte[SALT_LEN];
        RANDOM.nextBytes(salt);
        return build(keys, username, allowIp, access, digestType, hmacType, msgType,
                salt, randomDigits(), System.currentTimeMillis() / 1000L);
    }

    /** 便于用固定盐 / 随机值做互操作测试；生产代码走上面的重载。 */
    static byte[] build(SpaKeyMaterial keys, String username, String allowIp, String access,
                        String digestType, String hmacType, int msgType,
                        byte[] salt, String randValue, long timestamp) {
        if (keys == null) {
            throw new IllegalArgumentException("keys");
        }
        if (randValue == null || randValue.length() != 16) {
            throw new IllegalArgumentException("随机值必须是 16 位数字");
        }
        if (salt == null || salt.length != SALT_LEN) {
            throw new IllegalArgumentException("盐必须是 8 字节");
        }

        String user = b64(username.getBytes(StandardCharsets.UTF_8));
        // fwknop 把 allow IP 和访问端口写在同一个字段里，用逗号隔开
        String message = (allowIp == null || allowIp.isBlank() ? "0.0.0.0" : allowIp.strip())
                + "," + (access == null ? "" : access.strip());
        String encoded = randValue + ":" + user + ":" + timestamp + ":" + PROTOCOL_VERSION
                + ":" + msgType + ":" + b64(message.getBytes(StandardCharsets.UTF_8));
        String digest = b64(digest(digestType, encoded.getBytes(StandardCharsets.US_ASCII)));
        String plaintext = encoded + ":" + digest;

        byte[] salted = encrypt(plaintext.getBytes(StandardCharsets.US_ASCII), keys.encryptionKey(), salt);
        String encryptedB64 = b64(salted);
        if (!encryptedB64.startsWith(B64_SALT_PREFIX)) {
            throw new IllegalStateException("AES 包体缺少 Salted__ 前缀");
        }
        String hmacB64 = b64(hmac(hmacType, keys.hmacKey(), encryptedB64.getBytes(StandardCharsets.US_ASCII)));
        String wire = encryptedB64 + hmacB64;
        return wire.substring(B64_SALT_PREFIX.length()).getBytes(StandardCharsets.US_ASCII);
    }

    /** fwknop 的摘要算法名（md5 / sha1 / sha256 / sha384 / sha512）转 JCA 算法名。 */
    static String digestAlgorithm(String type) {
        return switch (normalize(type, "sha256")) {
            case "md5" -> "MD5";
            case "sha1" -> "SHA-1";
            case "sha384" -> "SHA-384";
            case "sha512" -> "SHA-512";
            default -> "SHA-256";
        };
    }

    /** fwknop 的 HMAC 算法名转 JCA 算法名。 */
    static String hmacAlgorithm(String type) {
        return switch (normalize(type, "sha256")) {
            case "md5" -> "HmacMD5";
            case "sha1" -> "HmacSHA1";
            case "sha384" -> "HmacSHA384";
            case "sha512" -> "HmacSHA512";
            default -> "HmacSHA256";
        };
    }

    private static String normalize(String type, String fallback) {
        String value = type == null ? "" : type.strip().toLowerCase(Locale.ROOT).replace("-", "");
        return value.isEmpty() ? fallback : value;
    }

    private static byte[] encrypt(byte[] plaintext, byte[] password, byte[] salt) {
        try {
            byte[] keyIv = evpBytesToKey(password, salt);
            SecretKeySpec key = new SecretKeySpec(keyIv, 0, KEY_LEN, "AES");
            IvParameterSpec iv = new IvParameterSpec(keyIv, KEY_LEN, IV_LEN);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key, iv);
            byte[] ciphertext = cipher.doFinal(plaintext);

            byte[] salted = new byte[SALT_LEN * 2 + ciphertext.length];
            System.arraycopy(SALTED_PREFIX, 0, salted, 0, SALT_LEN);
            System.arraycopy(salt, 0, salted, SALT_LEN, SALT_LEN);
            System.arraycopy(ciphertext, 0, salted, SALT_LEN * 2, ciphertext.length);
            return salted;
        } catch (Exception e) {
            throw new IllegalStateException("SPA 加密失败：" + e.getMessage(), e);
        }
    }

    /** OpenSSL EVP_BytesToKey(MD5, 1 轮)，输出 32 字节密钥 + 16 字节 IV。 */
    private static byte[] evpBytesToKey(byte[] password, byte[] salt) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] out = new byte[KEY_LEN + IV_LEN];
            byte[] previous = new byte[0];
            int offset = 0;
            while (offset < out.length) {
                md5.reset();
                if (previous.length > 0) {
                    md5.update(previous);
                }
                md5.update(password);
                md5.update(salt);
                previous = md5.digest();
                int copy = Math.min(previous.length, out.length - offset);
                System.arraycopy(previous, 0, out, offset, copy);
                offset += copy;
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("密钥派生失败：" + e.getMessage(), e);
        }
    }

    private static byte[] hmac(String type, byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(hmacAlgorithm(type));
            mac.init(new SecretKeySpec(key, hmacAlgorithm(type)));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败：" + e.getMessage(), e);
        }
    }

    private static byte[] digest(String type, byte[] data) {
        try {
            return MessageDigest.getInstance(digestAlgorithm(type)).digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("摘要计算失败：" + e.getMessage(), e);
        }
    }

    private static String b64(byte[] data) {
        return Base64.getEncoder().withoutPadding().encodeToString(data);
    }

    private static String randomDigits() {
        char[] digits = new char[16];
        digits[0] = (char) ('1' + RANDOM.nextInt(9));
        for (int i = 1; i < digits.length; i++) {
            digits[i] = (char) ('0' + RANDOM.nextInt(10));
        }
        return new String(digits);
    }
}
