package com.example.interview.common.util;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V9 SecretCipher：AES-GCM roundtrip、格式、密钥解析与解密失败语义。 */
class SecretCipherTest {

    private static final String RAW_KEY =
            Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptDecryptRoundtrip() {
        SecretCipher cipher = new SecretCipher(RAW_KEY);
        String secret = "sk-abc-1234567890-中文密钥";
        String enc = cipher.encrypt(secret);
        assertThat(enc).startsWith("v1:").isNotEqualTo(secret).doesNotContain("abc");
        assertThat(cipher.decrypt(enc)).isEqualTo(secret);
        // 随机 IV：两次加密产生不同密文，但都可解回
        assertThat(cipher.encrypt(secret)).isNotEqualTo(enc);
        assertThat(cipher.decrypt(cipher.encrypt(secret))).isEqualTo(secret);
    }

    @Test
    void decryptReturnsNullOnGarbageOrWrongKey() {
        SecretCipher cipher = new SecretCipher(RAW_KEY);
        assertThat(cipher.decrypt(null)).isNull();
        assertThat(cipher.decrypt("not-a-cipher")).isNull();
        assertThat(cipher.decrypt("v1:####")).isNull();
        // 密钥轮换：另一把钥匙解不开 → null（等价未配置）
        SecretCipher rotated = new SecretCipher(
                Base64.getEncoder().encodeToString(new byte[]{9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9,
                        9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9}));
        assertThat(rotated.decrypt(cipher.encrypt("sk-x"))).isNull();
    }

    @Test
    void blankKeyFallsBackToDevKeyAndBadKeyFailsFast() {
        // 空 → 内置开发密钥可用（WARN 日志）
        SecretCipher dev = new SecretCipher("");
        assertThat(dev.decrypt(dev.encrypt("sk-y"))).isEqualTo("sk-y");
        // 非 base64 / 长度非 32B → 启动即失败（配置错误显式暴露）
        assertThatThrownBy(() -> new SecretCipher("!!!not-base64!!!"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SecretCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 字节");
    }
}
