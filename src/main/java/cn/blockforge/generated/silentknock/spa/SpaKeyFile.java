package cn.blockforge.generated.silentknock.spa;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 读一个 fwknoprc 配置文件（模组里统一叫“SPA 配置文件”）。
 *
 * <p>文件内容交给 {@link FwknopRc} 解析，这里只负责三件事：
 * 找到文件、读成文本、以及兼容本模组旧版本写过的加密文件。
 *
 * <p>旧版本把密钥文件加密存盘（{@link SpaKeyVault#HEADER} 开头的格式），
 * 现在改成明文 fwknoprc 了，但旧文件仍然能读：先解密，再按 fwknoprc 解析。
 */
public final class SpaKeyFile {
    private SpaKeyFile() {
    }

    public static SpaProfile read(Path path) throws SpaKeyException {
        if (path == null) {
            throw new SpaKeyException("silent_knock.key.no_path");
        }
        if (!Files.isRegularFile(path)) {
            throw new SpaKeyException("silent_knock.key.not_found", path);
        }
        final String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            throw new SpaKeyException("silent_knock.key.read_failed", e.toString());
        }
        return parse(content);
    }

    /** 解析文件文本；旧版加密内容先解密。 */
    public static SpaProfile parse(String content) throws SpaKeyException {
        String plain = SpaKeyVault.isEncrypted(content) ? SpaKeyVault.decrypt(content) : content;
        List<String> lines = List.of(plain.split("\\R"));
        return FwknopRc.parse(lines);
    }
}
