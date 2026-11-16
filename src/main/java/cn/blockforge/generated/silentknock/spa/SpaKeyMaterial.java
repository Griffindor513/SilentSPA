package cn.blockforge.generated.silentknock.spa;

/**
 * 从密钥文件里读出来的原始密钥字节。
 *
 * <p>fwknop 的 SPA 包是“先加密、再对整包做 HMAC”，所以两把钥匙都需要：
 * <ul>
 *   <li>{@code encryptionKey}：fwknop 的 Rijndael（AES）密钥，用来加密包体；</li>
 *   <li>{@code hmacKey}：HMAC 密钥，用来给加密后的整包签名。</li>
 * </ul>
 */
public record SpaKeyMaterial(byte[] encryptionKey, byte[] hmacKey) {
}
