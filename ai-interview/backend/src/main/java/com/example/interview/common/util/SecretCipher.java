package com.example.interview.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 敏感值加密（V9 用户级 AI 配置）：AES-256-GCM，随机 12B IV，密文格式
 * {@code v1:base64(iv‖ct)}。密钥取 {@code app.cipher-key}（base64 编码的 32 字节，
 * 生产经 APP_CIPHER_KEY 注入）；为空时使用内置开发密钥并 WARN（本地演示可用）。
 * 解密失败返回 null（密钥轮换后等价“未配置”，由调用方回退默认行为）。
 */
@Slf4j
@Component
public class SecretCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${app.cipher-key:}") String base64Key) {
        this.key = resolveKey(base64Key);
    }

    /** 明文 → v1:base64(iv‖ciphertext)；plain 为 null 抛参数异常（调用方保证非空）。 */
    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            throw new IllegalArgumentException("加密内容不能为空");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            // JDK 自带算法不存在此分支，仅防御性兜底
            throw new IllegalStateException("加密失败", e);
        }
    }

    /** 密文 → 明文；格式非法或解密失败（密钥轮换/数据损坏）返回 null 并 WARN。 */
    public String decrypt(String cipherText) {
        if (cipherText == null || !cipherText.startsWith(PREFIX)) {
            log.warn("密文格式非法（缺 v1: 前缀），按未配置处理");
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(cipherText.substring(PREFIX.length()));
            if (all.length <= IV_BYTES) {
                return null;
            }
            GCMParameterSpec spec = new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            byte[] plain = cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("解密失败（密钥轮换或数据损坏），按未配置处理：{}", e.getMessage());
            return null;
        }
    }

    /** base64 32B 密钥；空 → 内置开发密钥（WARN）；非法/长度不符 → 启动即失败（配置错误应显式暴露）。 */
    private SecretKey resolveKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            log.warn("app.cipher-key 未配置，使用内置开发密钥（生产环境必须配置 APP_CIPHER_KEY）");
            return devKey();
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.cipher-key 不是合法的 base64", e);
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("app.cipher-key 解码后必须为 32 字节（AES-256），当前 " + bytes.length);
        }
        return new SecretKeySpec(bytes, "AES");
    }

    private SecretKey devKey() {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest("ai-interview-dev-cipher-key".getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(bytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("初始化开发密钥失败", e);
        }
    }
}
