package cn.blockforge.generated.silentknock.spa;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 现场生成一对新的 fwknop 密钥（和 {@code fwknop --key-gen --use-hmac} 的产物等价）。
 *
 * <p>生成的两把密钥要同时存在于客户端和服务器端：客户端用它算敲门包，
 * 服务器把它写进 {@code access.conf} 校验。所以界面上会把它们填进输入框，
 * 玩家可以原样用，也可以换成自己已有的两串 Base64 密钥。
 */
public final class SpaKeyGenerator {
    /** Rijndael 加密密钥 32 字节（Base64 后 44 字符）。 */
    private static final int ENCRYPTION_KEY_BYTES = 32;
    /** HMAC 密钥 64 字节（Base64 后 88 字符）。 */
    private static final int HMAC_KEY_BYTES = 64;
    private static final SecureRandom RANDOM = new SecureRandom();

    private SpaKeyGenerator() {
    }

    /** 一次生成的结果，两把密钥都是 Base64 文本。 */
    public static final class Keys {
        public final String encryptionBase64;
        public final String hmacBase64;

        Keys(String encryptionBase64, String hmacBase64) {
            this.encryptionBase64 = encryptionBase64;
            this.hmacBase64 = hmacBase64;
        }
    }

    public static Keys generate() {
        return new Keys(randomBase64(ENCRYPTION_KEY_BYTES), randomBase64(HMAC_KEY_BYTES));
    }

    private static String randomBase64(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
