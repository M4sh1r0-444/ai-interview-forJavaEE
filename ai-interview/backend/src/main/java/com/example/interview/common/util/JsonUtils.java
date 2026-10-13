package com.example.interview.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * ObjectMapper 单例（计划 §4.3 common/util/JsonUtils）。
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonUtils() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String toJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    public static <T> T parse(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 反序列化失败: " + e.getMessage(), e);
        }
    }

    public static <T> T parse(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 反序列化失败: " + e.getMessage(), e);
        }
    }
}
